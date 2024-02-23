package codes.rafael.modulemaker;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.*;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;
import java.util.zip.ZipEntry;

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

    /**
     * Defines an output timestamp for JAR entries.
     */
    @Parameter(defaultValue = "${project.build.outputTimestamp}")
    private String outputTimestamp;

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
        JarEntryCreator creator;
        if (outputTimestamp == null) {
            creator = new JarEntryCreator.Simple();
        } else {
            long time;
            try {
                time = Long.parseLong(outputTimestamp) / 1000;
            } catch (RuntimeException e) {
                if (outputTimestamp.length() < 2) {
                    time = -1;
                } else {
                    try {
                        Class<?> offsetDateTime = Class.forName("java.time.OffsetDateTime");
                        Object parsed = offsetDateTime.getMethod("parse", CharSequence.class).invoke(null, outputTimestamp);
                        Class<?> zoneOffset = Class.forName("java.time.ZoneOffset");
                        parsed = offsetDateTime.getMethod("withOffsetSameInstant", zoneOffset).invoke(parsed, zoneOffset.getField("UTC").get(null));
                        Class<?> temporalUnit = Class.forName("java.time.temporal.TemporalUnit");
                        Class<?> chronoUnit = Class.forName("java.time.temporal.ChronoUnit");
                        parsed = offsetDateTime.getMethod("truncatedTo", temporalUnit).invoke(parsed, chronoUnit.getField("SECONDS").get(null));
                        time = (Long) offsetDateTime.getMethod("toEpochSecond").invoke(parsed);
                    } catch (Exception ignored) {
                        throw e;
                    }
                }
            }
            if (time < 0) {
                creator = new JarEntryCreator.Simple();
            } else {
                try {
                    creator = new JarEntryCreator.WithOutputTimestampAndMore(time);
                } catch (Exception ignored) {
                    creator = new JarEntryCreator.WithOutputTimestamp(time);
                }
            }
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
                JarOutputStream outputStream = new JarOutputStream(new FileOutputStream(targetJar));
                try {
                    if (manifest != null) {
                        outputStream.putNextEntry(creator.toEntry(JarFile.MANIFEST_NAME));
                        manifest.write(outputStream);
                        outputStream.closeEntry();
                    }
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
                    outputStream.putNextEntry(creator.toEntry(filename));
                    outputStream.write(makeModuleInfo());
                    outputStream.closeEntry();
                    for (String directory : multiReleaseDirectories) {
                        outputStream.putNextEntry(creator.toEntry(directory));
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

    interface JarEntryCreator {

        JarEntry toEntry(String name);

        class Simple implements JarEntryCreator {
            @Override
            public JarEntry toEntry(String name) {
                return new JarEntry(name);
            }
        }

        class WithOutputTimestamp implements JarEntryCreator {

            private final long time;

            public WithOutputTimestamp(long time) {
                this.time = time;
            }

            @Override
            public JarEntry toEntry(String name) {
                JarEntry entry = new JarEntry(name);
                entry.setTime(time / 1000);
                return entry;
            }
        }

        class WithOutputTimestampAndMore implements JarEntryCreator {

            private final long time;

            private final Method fromMillis, setCreationTime, setLastAccessTime, setLastModifiedTime;

            public WithOutputTimestampAndMore(long time) throws Exception {
                this.time = time;
                Class<?> fileTime = Class.forName("java.nio.file.attribute.FileTime");
                fromMillis = fileTime.getMethod("from", long.class, TimeUnit.class);
                setCreationTime = ZipEntry.class.getMethod("setCreationTime", fileTime);
                setLastAccessTime = ZipEntry.class.getMethod("setLastAccessTime", fileTime);
                setLastModifiedTime = ZipEntry.class.getMethod("setLastModifiedTime", fileTime);
            }

            @Override
            public JarEntry toEntry(String name) {
                JarEntry entry = new JarEntry(name);
                entry.setTime(time);
                try {
                    Object fileTime = fromMillis.invoke(null, time, TimeUnit.SECONDS);
                    setCreationTime.invoke(entry, fileTime);
                    setLastAccessTime.invoke(entry, fileTime);
                    setLastModifiedTime.invoke(entry, fileTime);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return entry;
            }
        }
    }
}
