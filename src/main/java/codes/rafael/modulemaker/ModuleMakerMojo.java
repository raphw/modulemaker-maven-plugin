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

@Mojo(name = "make-module", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true)
public class ModuleMakerMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    private String outputDirectory;

    @Parameter(name = "java-version", defaultValue = "9")
    private int javaVersion;

    @Parameter(required = true)
    private String name;

    @Parameter
    private String version;

    @Parameter(required = true)
    private String packages;

    @Parameter
    private String requires;

    @Parameter(name = "static-requires")
    private String staticRequires;

    @Parameter
    private String exports;

    @Parameter
    private String opens;

    @Parameter(name = "qualified-exports")
    private List<QualifiedPackage> qualifiedExports;

    @Parameter(name = "qualified-opens")
    private List<QualifiedPackage> qualifiedOpens;

    @Parameter(name = "main-class")
    private String mainClass;

    @Parameter
    private String uses;

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
        if (packages.trim().length() == 0) {
            throw new MojoExecutionException("Must export at least one package");
        }
        Set<String> previousPackages = new HashSet<String>();
        for (String aPackage : packages.split(",")) {
            if (!previousPackages.add(aPackage.trim())) {
                throw new MojoExecutionException("Duplicate package: " + aPackage.trim());
            }
            moduleVisitor.visitPackage(aPackage.trim());
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
                if (!previousExports.add(anExport.trim())) {
                    throw new MojoExecutionException("Duplicate export: " + anExport.trim());
                }
                moduleVisitor.visitExport(anExport.trim(), 0, (String[]) null);
            }
        }
        if (qualifiedExports != null) {
            for (QualifiedPackage qualifiedPackage : qualifiedExports) {
                String[] modules = qualifiedPackage.modules.split(",");
                Set<String> previousModules = new HashSet<String>();
                for (int index = 0; index < modules.length; index++) {
                    if (!previousModules.add(modules[index])) {
                        throw new MojoExecutionException("Duplicate module: " + modules[index].trim());
                    }
                    modules[index] = modules[index].trim();
                }
                for (String anExport : qualifiedPackage.packages.split(",")) {
                    if (!previousExports.add(anExport.trim())) {
                        throw new MojoExecutionException("Duplicate export: " + anExport.trim());
                    }
                    moduleVisitor.visitExport(anExport.trim(), 0, modules);
                }
            }
        }
        Set<String> previousOpens = new HashSet<String>();
        if (opens != null) {
            for (String anOpen : opens.split(",")) {
                if (!previousOpens.add(anOpen.trim())) {
                    throw new MojoExecutionException("Duplicate export: " + anOpen.trim());
                }
                moduleVisitor.visitOpen(anOpen.trim(), 0, (String[]) null);
            }
        }
        if (qualifiedOpens != null) {
            for (QualifiedPackage qualifiedPackage : qualifiedOpens) {
                String[] modules = qualifiedPackage.modules.split(",");
                Set<String> previousModules = new HashSet<String>();
                for (int index = 0; index < modules.length; index++) {
                    if (!previousModules.add(modules[index])) {
                        throw new MojoExecutionException("Duplicate module: " + modules[index].trim());
                    }
                    modules[index] = modules[index].trim();
                }
                for (String anOpen : qualifiedPackage.packages.split(",")) {
                    if (!previousOpens.add(anOpen.trim())) {
                        throw new MojoExecutionException("Duplicate export: " + anOpen.trim());
                    }
                    moduleVisitor.visitOpen(anOpen.trim(), 0, modules);
                }
            }
        }
        if (mainClass != null) {
            moduleVisitor.visitMainClass(mainClass.trim());
        }
        if (uses != null) {
            Set<String> previousUses = new HashSet<String>();
            for (String aUse : uses.split(",")) {
                if (!previousUses.add(aUse.trim())) {
                    throw new MojoExecutionException("Duplicate use: " + aUse.trim());
                }
                moduleVisitor.visitUse(aUse.trim());
            }
        }
        if (provides != null) {
            Set<String> previousProvides = new HashSet<String>();
            for (Provide provide : provides) {
                if (!previousProvides.add(provide.service.trim())) {
                    throw new MojoExecutionException("Duplicate service: " + provide.service.trim());
                }
                String[] providers = provide.providers.split(",");
                Set<String> previousImplementations = new HashSet<String>();
                for (int index = 0; index < providers.length; index++) {
                    if (!previousImplementations.add(providers[index])) {
                        throw new MojoExecutionException("Duplicate provider: " + providers[index].trim());
                    }
                    providers[index] = providers[index].trim();
                }
                moduleVisitor.visitProvide(provide.service.trim(), providers);
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
