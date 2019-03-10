package codes.rafael.modulemaker;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * A Maven plugin for injecting a {@code module-info.class} into an existing jar file.
 */
@Mojo(name = "inject-module", defaultPhase = LifecyclePhase.PACKAGE)
public class ModuleInjectMojo extends AbstractModuleMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Component
    private MavenProjectHelper projectHelper;

    @Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
    private String directory;

    @Parameter(defaultValue = "${project.build.finalName}", required = true, readonly = true)
    private String finalName;

    /**
     * Specifies the location of the jar file which should be enhanced with a {@code module-info.class} file.
     */
    @Parameter
    private String source;

    /**
     * The classifier to add to any additional artifact of this build that contains the {@code module-info.class} file.
     * If {@code replace} is set to {@code true}, this classifier is used for the name of the intermediate jar file. If
     * it is empty, the standard classifier {@code modularized} is used.
     */
    @Parameter(defaultValue = "modularized")
    private String classifier;

    /**
     * {@code true} if the original artifact should be replaced with a jar file containing the {@code module-info.class}.
     */
    @Parameter(defaultValue = "true")
    private boolean replace;

    /**
     * Determines if a folder entry should be created for a {@code module-info.class} file that is placed in a multi-release
     * jar if {@code META-INF/versions/[java]/} does not exist.
     */
    @Parameter(defaultValue = "true")
    private boolean createMultiReleaseFolderEntry;

    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        String filename = filename();
        File sourceJar;
        if (source == null) {
            sourceJar = new File(directory, finalName + ".jar");
        } else {
            sourceJar = new File(source);
        }
        if (!sourceJar.isFile()) {
            throw new MojoExecutionException("Could not locate source jar: " + sourceJar);
        }
        String classifier = this.classifier == null || this.classifier.isEmpty() ? "modularized" : this.classifier;
        try {
            File targetJar = new File(directory, finalName + "-" + classifier + ".jar");
            if (!targetJar.isFile() && !targetJar.createNewFile()) {
                throw new MojoExecutionException("Target jar could not be created and did not exist from before: " + targetJar);
            }
            JarInputStream inputStream = new JarInputStream(new FileInputStream(sourceJar));
            try {
                if (!targetJar.isFile() && !targetJar.createNewFile()) {
                    throw new MojoFailureException("Could not create target jar: " + targetJar);
                }
                Manifest manifest = inputStream.getManifest();
                JarOutputStream outputStream = manifest == null
                        ? new JarOutputStream(new FileOutputStream(targetJar))
                        : new JarOutputStream(new FileOutputStream(targetJar), manifest);
                try {
                    Set<String> multiReleaseDirectories = multirelease && createMultiReleaseFolderEntry
                            ? new HashSet<String>(Arrays.asList("META-INF/", "META-INF/versions/", "META-INF/versions/" + javaVersion + "/"))
                            : Collections.<String>emptySet();
                    JarEntry jarEntry;
                    while ((jarEntry = inputStream.getNextJarEntry()) != null) {
                        if (jarEntry.getName().equals(filename)) {
                            inputStream.closeEntry();
                            getLog().warn("Ignoring preexisting module-info.class in " + sourceJar);
                            continue;
                        } else if (multiReleaseDirectories.remove(jarEntry.getName())) {
                            getLog().debug("Discovered multi-version jar file location: " + jarEntry.getName());
                        }
                        outputStream.putNextEntry(jarEntry);
                        byte[] buffer = new byte[1024];
                        int index;
                        while ((index = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, index);
                        }
                        inputStream.closeEntry();
                        outputStream.closeEntry();
                    }
                    outputStream.putNextEntry(new JarEntry(filename));
                    outputStream.write(makeModuleInfo());
                    outputStream.closeEntry();
                    for (String directory : multiReleaseDirectories) {
                        outputStream.putNextEntry(new JarEntry(directory));
                        outputStream.closeEntry();
                    }
                } finally {
                    outputStream.close();
                }
            } finally {
                inputStream.close();
            }
            if (replace) {
                if (!sourceJar.delete() || !targetJar.renameTo(sourceJar)) {
                    throw new MojoFailureException("Could not replace source jar: " + sourceJar);
                }
                getLog().info("Injected module-info.class into " + sourceJar);
            } else {
                projectHelper.attachArtifact(project, project.getArtifact().getType(), classifier, targetJar);
                getLog().info("Attached artifact with module-info.class as " + targetJar);
            }
        } catch (IOException exception) {
            throw new MojoFailureException("Could not write or read artifact", exception);
        }
    }
}
