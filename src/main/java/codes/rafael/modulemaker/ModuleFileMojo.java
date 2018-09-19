package codes.rafael.modulemaker;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A Maven plugin for creating a {@code module-info.class}.
 * A Maven plugin for creating a {@code module-info.class} within the {@code /classes} directory.
 */
@Mojo(name = "make-module", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true)
public class ModuleFileMojo extends AbstractModuleMojo {

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    private String outputDirectory;

    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        File outputDirectory = new File(this.outputDirectory);
        if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
            throw new MojoExecutionException("Could not read or create directory: " + outputDirectory);
        }
        try {
            OutputStream out = new FileOutputStream(new File(outputDirectory, "module-info.class"));
            try {
                out.write(makeModuleInfo());
                out.write(makeModuleInfo());
            } finally {
                out.close();
            }
        } catch (IOException e) {
            throw new MojoFailureException("Cannot write to " + outputDirectory, e);
        }
        getLog().info("Added module-info.class to " + outputDirectory);
    }
}
