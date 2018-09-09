package codes.rafael.modulemaker;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A Maven plugin for creating a {@code module-info.class}.
 */
@Mojo(name = "make-module", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true)
public class ModuleMakerMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    private String outputDirectory;

    /**
     * The Java version in which the {@code module-info.class} file should be compiled.
     */
    @Parameter(name = "java-version", defaultValue = "9")
    private int javaVersion;

    /**
     * The name of the module.
     */
    @Parameter(required = true)
    private String name;

    /**
     * The version of the module (optional).
     */
    @Parameter
    private String version;

    /**
     * A comma-separated list of packages of the module. This attribute is optional but offers an optimization
     * that is normally applied by the Java JAR tool. By naming all packages, the runtime does not need to scan
     * the jar file upon loading it but can use the list of explicitly named packages.
     */
    @Parameter
    private String packages;

    /**
     * A comma-separated list of required modules.
     */
    @Parameter
    private String requires;

    /**
     * A comma-separated list of statically required modules.
     */
    @Parameter(name = "static-requires")
    private String staticRequires;

    /**
     * A comma-separated list of exported packages.
     */
    @Parameter
    private String exports;

    /**
     * A comma-separated list of opened packages.
     */
    @Parameter
    private String opens;

    /**
     * A list of qualified exports.
     */
    @Parameter(name = "qualified-exports")
    private List<QualifiedPackage> qualifiedExports;

    /**
     * A list of qualified opens.
     */
    @Parameter(name = "qualified-opens")
    private List<QualifiedPackage> qualifiedOpens;

    /**
     * The main class of this module (optional).
     */
    @Parameter(name = "main-class")
    private String mainClass;

    /**
     * A comma-separated list of used services.
     */
    @Parameter
    private String uses;

    /**
     * A list of provided services.
     */
    @Parameter
    private List<Provide> provides;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        ClassWriter classWriter = new ClassWriter(0);
        if (javaVersion < 9) {
            throw new MojoExecutionException("Invalid Java version for module-info: " + javaVersion);
        }
        classWriter.visit(44 + javaVersion, Opcodes.ACC_MODULE, "module-info", null, null, null);
        ModuleVisitor moduleVisitor = classWriter.visitModule(name, 0, version);
        if (packages != null) {
            Set<String> previousPackages = new HashSet<String>();
            for (String aPackage : packages.split(",")) {
                if (!previousPackages.add(aPackage.trim())) {
                    throw new MojoExecutionException("Duplicate package: " + aPackage.trim());
                }
                moduleVisitor.visitPackage(aPackage.trim());
            }
        }
        Set<String> previousRequires = new HashSet<String>();
        if (requires != null) {
            for (String aRequire : requires.split(",")) {
                if (!previousRequires.add(aRequire.trim())) {
                    throw new MojoExecutionException("Duplicate require: " + aRequire.trim());
                }
                moduleVisitor.visitRequire(aRequire.trim(), 0, null);
            }
        }
        if (staticRequires != null) {
            for (String aRequire : staticRequires.split(",")) {
                if (!previousRequires.add(aRequire.trim())) {
                    throw new MojoExecutionException("Duplicate require: " + aRequire.trim());
                }
                moduleVisitor.visitRequire(aRequire.trim(), Opcodes.ACC_STATIC_PHASE, null);
            }
        }
        if (!previousRequires.contains("java.base")) {
            moduleVisitor.visitRequire("java.base", Opcodes.ACC_MANDATED, null);
        }
        Set<String> previousExports = new HashSet<String>();
        if (exports != null) {
            for (String anExport : exports.split(",")) {
                String formattedExport = anExport.trim().replace('.', '/');
                if (!previousExports.add(formattedExport)) {
                    throw new MojoExecutionException("Duplicate export: " + anExport.trim());
                }
                moduleVisitor.visitExport(formattedExport, 0, (String[]) null);
            }
        }
        if (qualifiedExports != null) {
            for (QualifiedPackage qualifiedPackage : qualifiedExports) {
                String[] modules = qualifiedPackage.modules.split(",");
                Set<String> previousModules = new HashSet<String>();
                for (int index = 0; index < modules.length; index++) {
                    if (!previousModules.add(modules[index].trim())) {
                        throw new MojoExecutionException("Duplicate module: " + modules[index].trim());
                    }
                    modules[index] = modules[index].trim();
                }
                for (String anExport : qualifiedPackage.packages.split(",")) {
                    String formattedExport = anExport.trim().replace('.', '/');
                    if (!previousExports.add(formattedExport)) {
                        throw new MojoExecutionException("Duplicate export: " + anExport.trim());
                    }
                    moduleVisitor.visitExport(formattedExport, 0, modules);
                }
            }
        }
        Set<String> previousOpens = new HashSet<String>();
        if (opens != null) {
            for (String anOpen : opens.split(",")) {
                String formattedOpen = anOpen.trim().replace('.', '/');
                if (!previousOpens.add(formattedOpen)) {
                    throw new MojoExecutionException("Duplicate open: " + anOpen.trim());
                }
                moduleVisitor.visitOpen(formattedOpen, 0, (String[]) null);
            }
        }
        if (qualifiedOpens != null) {
            for (QualifiedPackage qualifiedPackage : qualifiedOpens) {
                String[] modules = qualifiedPackage.modules.split(",");
                Set<String> previousModules = new HashSet<String>();
                for (int index = 0; index < modules.length; index++) {
                    if (!previousModules.add(modules[index].trim())) {
                        throw new MojoExecutionException("Duplicate module: " + modules[index].trim());
                    }
                    modules[index] = modules[index].trim();
                }
                for (String anOpen : qualifiedPackage.packages.split(",")) {
                    String formattedOpen = anOpen.trim().replace('.', '/');
                    if (!previousOpens.add(formattedOpen)) {
                        throw new MojoExecutionException("Duplicate open: " + anOpen.trim());
                    }
                    moduleVisitor.visitOpen(formattedOpen, 0, modules);
                }
            }
        }
        if (mainClass != null) {
            moduleVisitor.visitMainClass(mainClass.trim());
        }
        if (uses != null) {
            Set<String> previousUses = new HashSet<String>();
            for (String aUse : uses.split(",")) {
                String formattedUse = aUse.trim().replace('.', '/');
                if (!previousUses.add(formattedUse)) {
                    throw new MojoExecutionException("Duplicate use: " + aUse.trim());
                }
                moduleVisitor.visitUse(formattedUse);
            }
        }
        if (provides != null) {
            Set<String> previousServices = new HashSet<String>();
            for (Provide provide : provides) {
                String[] providers = provide.providers.split(",");
                Set<String> previousProviders = new HashSet<String>();
                for (int index = 0; index < providers.length; index++) {
                    String formattedProvider = providers[index].trim().replace('.', '/');
                    if (!previousProviders.add(formattedProvider)) {
                        throw new MojoExecutionException("Duplicate provider: " + providers[index].trim());
                    }
                    providers[index] = formattedProvider;
                }
                for (String service : provide.services.split(",")) {
                    String formattedService = service.trim().replace('.', '/');
                    if (!previousServices.add(formattedService)) {
                        throw new MojoExecutionException("Duplicate service: " + service.trim());
                    }
                    moduleVisitor.visitProvide(formattedService, providers);
                }
            }
        }
        moduleVisitor.visitEnd();
        classWriter.visitEnd();
        File outputDirectory = new File(this.outputDirectory);
        if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
            throw new MojoExecutionException("Could not create directory: " + outputDirectory);
        }
        try {
            OutputStream out = new FileOutputStream(new File(outputDirectory, "module-info.class"));
            try {
                out.write(classWriter.toByteArray());
            } finally {
                out.close();
            }
        } catch (IOException e) {
            throw new MojoFailureException("Cannot write to " + outputDirectory, e);
        }
    }
}
