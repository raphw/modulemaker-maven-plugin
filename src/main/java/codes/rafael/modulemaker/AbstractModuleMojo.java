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

public abstract class AbstractModuleMojo extends AbstractMojo {

    /**
     * The Java version in which the {@code module-info.class} file should be compiled.
     */
    @Parameter(name = "java-version", defaultValue = "9")
    protected int javaVersion;

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
     * Determines if the {@code module-info.class} is added as a class file of a multi-release jar file.
     * To function correctly, using this option requires a manifest declaring {@code Multi-Release: true}.
     */
    @Parameter(required = true, defaultValue = "false")
    protected boolean multirelease;

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
        if (javaVersion < 9) {
            throw new MojoExecutionException("Invalid Java version for module-info: " + javaVersion);
        }
        doExecute();
    }

    protected abstract void doExecute() throws MojoExecutionException, MojoFailureException;

    protected String filename() {
        return (multirelease ? ("META-INF/versions/" + javaVersion + "/") : "") + "module-info.class";
    }

    protected byte[] makeModuleInfo() throws MojoExecutionException {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(44 + javaVersion, Opcodes.ACC_MODULE, "module-info", null, null, null);
        ModuleVisitor moduleVisitor = classWriter.visitModule(name, 0, version);
        if (packages != null) {
            Set<String> previousPackages = new HashSet<String>();
            for (String aPackage : packages.split(",")) {
                if (!previousPackages.add(aPackage.trim())) {
                    throw new MojoExecutionException("Duplicate package: " + aPackage.trim());
                }
                moduleVisitor.visitPackage(aPackage.trim().replace('.', '/'));
            }
        }
        Set<String> previousRequires = new HashSet<String>();
        if (requires != null) {
            for (String module : requires.split(",")) {
                if (!previousRequires.add(module.trim())) {
                    throw new MojoExecutionException("Duplicate require: " + module.trim());
                }
                moduleVisitor.visitRequire(module.trim(), 0, null);
            }
        }
        if (staticRequires != null) {
            for (String module : staticRequires.split(",")) {
                if (!previousRequires.add(module.trim())) {
                    throw new MojoExecutionException("Duplicate require: " + module.trim());
                }
                moduleVisitor.visitRequire(module.trim(), Opcodes.ACC_STATIC_PHASE, null);
            }
        }
        if (!previousRequires.contains("java.base")) {
            moduleVisitor.visitRequire("java.base", Opcodes.ACC_MANDATED, null);
        }
        Set<String> previousExports = new HashSet<String>();
        if (exports != null) {
            for (String aPackage : exports.split(",")) {
                if (!previousExports.add(aPackage.trim())) {
                    throw new MojoExecutionException("Duplicate export: " + aPackage.trim());
                }
                moduleVisitor.visitExport(aPackage.trim().replace('.', '/'), 0, (String[]) null);
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
                for (String aPackage : qualifiedPackage.packages.split(",")) {
                    if (!previousExports.add(aPackage.trim())) {
                        throw new MojoExecutionException("Duplicate export: " + aPackage.trim());
                    }
                    moduleVisitor.visitExport(aPackage.trim().replace('.', '/'), 0, modules);
                }
            }
        }
        Set<String> previousOpens = new HashSet<String>();
        if (opens != null) {
            for (String aPackage : opens.split(",")) {
                if (!previousOpens.add(aPackage.trim())) {
                    throw new MojoExecutionException("Duplicate export: " + aPackage.trim());
                }
                moduleVisitor.visitOpen(aPackage.trim().replace('.', '/'), 0, (String[]) null);
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
                for (String aPackage : qualifiedPackage.packages.split(",")) {
                    if (!previousOpens.add(aPackage.trim())) {
                        throw new MojoExecutionException("Duplicate export: " + aPackage.trim());
                    }
                    moduleVisitor.visitOpen(aPackage.trim().replace('.', '/'), 0, modules);
                }
            }
        }
        if (mainClass != null) {
            moduleVisitor.visitMainClass(mainClass.trim());
        }
        if (uses != null) {
            Set<String> previousUses = new HashSet<String>();
            for (String type : uses.split(",")) {
                if (!previousUses.add(type.trim())) {
                    throw new MojoExecutionException("Duplicate use: " + type.trim());
                }
                moduleVisitor.visitUse(type.trim().replace('.', '/'));
            }
        }
        if (provides != null) {
            Set<String> previousServices = new HashSet<String>();
            for (Provide provide : provides) {
                String[] providers = provide.providers.split(",");
                Set<String> previousProviders = new HashSet<String>();
                for (int index = 0; index < providers.length; index++) {
                    if (!previousProviders.add(providers[index].trim())) {
                        throw new MojoExecutionException("Duplicate provider: " + providers[index].trim());
                    }
                    providers[index] = providers[index].trim().replace('.', '/');
                }
                for (String type : provide.services.split(",")) {
                    if (!previousServices.add(type.trim())) {
                        throw new MojoExecutionException("Duplicate service: " + type.trim());
                    }
                    moduleVisitor.visitProvide(type.trim().replace('.', '/'), providers);
                }
            }
        }
        moduleVisitor.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }
}
