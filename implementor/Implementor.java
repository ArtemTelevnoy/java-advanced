package info.kgeorgiy.ja.televnoi.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Objects;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static java.lang.String.format;
import static java.lang.System.err;

/**
 * This class generate class that implements interface
 *
 * @author Artem Televnoy
 */
public class Implementor implements JarImpler {

    /**
     * Suffix of Creating file
     */
    private static final String SUFFIX = "Impl";

    /**
     * Extension of creating file
     */
    private static final String EXTENSION = ".java";

    /**
     * Tab symbol
     */
    private static final String TAB = "    ";

    /**
     * Default constructor
     */
    public Implementor() {
    }

    /**
     * Do implementation of class. Format depends on mods:
     * <ol>
     *      <li>default mod: {@code .java} files</li>
     *      <li>{@code -jar} mod: {@code .jar} files</li>
     * </ol>
     *
     * @param args command line arguments. Must be 2 or 3 and correct
     */
    public static void main(String[] args) {
        if (args.length == 2 || args.length == 3) {
            int i = args.length == 2 ? 0 : 1;
            try {
                if (args.length == 2) {
                    new Implementor().implement(Class.forName(args[i]), Paths.get(args[i + 1]));
                } else if (args[0].equals("-jar")){
                    new Implementor().implementJar(Class.forName(args[i]), Paths.get(args[i + 1]));
                } else {
                    err.println("Incorrect flag: only -jar");
                }
            } catch (ImplerException e) {
                err.println(e.getMessage());
            } catch (ClassNotFoundException e) {
                err.println("Invalid Class<?> in argument: " + e.getMessage());
            } catch (InvalidPathException e) {
                err.println("Invalid path of root dir in argument: " + e.getMessage());
            }
            return;
        }
        err.printf("arguments must be%n -jar <classname> <.jar output file> if -jar mode%n <classname> <.jar output file> if default mod%n");
    }

    /**
     * Generate implementation of {@link Class} {@code token} on {@link Path} {@code path}
     *
     * @param token {@link Class} to create implementation
     * @param root {@link Path} to creating implementation
     * @throws ImplerException if file didn't create or error when writing in this file or {@link Implementor#buildFile} throw this exception
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        if (!token.isInterface() || Modifier.isPrivate(token.getModifiers())) {
            except("Unsupported generation: only public interfaces");
        }

        final Path p = tryGetPath(Objects.requireNonNull(token), Objects.requireNonNull(root), EXTENSION);
        if (p.getParent() != null) {
            tryCreateDir(p.getParent());
        }

        final String implClass = buildFile(new StringBuilder(), token);
        try (final BufferedWriter wr = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            try {
                wr.write(implClass);
            } catch (IOException e) {
                except("Error writing file", e);
            }
        } catch (IOException | SecurityException e) {
            except("Error creating file", e);
        }
    }

    /**
     * Writes class realisation of {@code token}
     *
     * @param builder it's {@link StringBuilder} where {@code token} realisation will write
     * @param token it's {@link Class} from which class realisation will be implemented
     * @return {@link String} view of implementation {@code token}
     * @throws ImplerException if {@link Implementor#writeMethods} throw {@link SecurityException}
     */
    private static String buildFile(final StringBuilder builder, final Class<?> token) throws ImplerException {
        try {
            if (token.getProtectionDomain().getCodeSource() != null) {
                builder.append(format("package %s;%n%n", validator(token.getPackageName())));
            }
            builder.append(format("public class %s%s implements %s {%n%n", validator(token.getSimpleName()),
                    SUFFIX, validator(token.getCanonicalName())));
            writeMethods(builder, token);
            builder.append(format("}%n"));
        } catch (SecurityException e) {
            except("Error building file", e);
        }
        return builder.toString();
    }

    /**
     * Writes all methods from {@code token}
     *
     * @param builder it's {@link StringBuilder} where methods will write
     * @param token it's {@link Class} from which methods will be implemented
     * @throws SecurityException if {@link Class#getMethods()} throw this
     */
    private static void writeMethods(final StringBuilder builder, final Class<?> token) throws SecurityException {
        for (Method el : token.getMethods()) {
            builder.append(format("public %s %s(", validator(el.getReturnType().getCanonicalName()), validator(el.getName())));
            final Class<?>[] paramTypes = el.getParameterTypes();
            final Parameter[] params = el.getParameters();
            for (int i = 0; i < paramTypes.length; i++) {
                builder.append(format("%s %s", validator(paramTypes[i].getCanonicalName()), validator(params[i].getName())));
                if (i != paramTypes.length - 1) {
                    builder.append(", ");
                }
            }

            builder.append(format(") {%n"));
            if (!el.getReturnType().equals(void.class)) {
                final String returnVal = el.getReturnType().equals(boolean.class) ? "false" :
                        el.getReturnType().isPrimitive() ? "0" : "null";
                builder.append(format("%s%s%s %s;%n", TAB, TAB, "return", returnVal));
            }
            builder.append(format("%s}%n%n", TAB));
        }
    }

    /**
     * trying getting {@link Path} to file
     *
     * @param token it's {@link Class} for which you need to get {@link Path}
     * @param root it's root directory where will be created {@code token} on him path
     * @param format it's extension of file
     * @return {@link Path} for writing {@code token} implementation
     * @throws ImplerException if creating {@link Path} is invalid
     */
    private static Path tryGetPath(final Class<?> token, final Path root, final String format) throws ImplerException {
        Path path = null;
        try {
            path = token.getProtectionDomain().getCodeSource() == null ? Path.of(token.getSimpleName() + SUFFIX + format) :
                    Path.of(token.getPackageName().replace('.', File.separatorChar), token.getSimpleName() + SUFFIX + format);
        } catch (InvalidPathException e) {
            except("Invalid path", e);
        }
        return root.resolve(path);
    }

    /**
     * Creates dir on {@code path}
     *
     * @param path {@link Path} for creating directory
     * @throws ImplerException if directory on {@code path} wasn't created
     */
    private static void tryCreateDir(final Path path) throws ImplerException {
        try {
            Files.createDirectories(path);
        } catch (IOException | SecurityException e) {
            except("Error when creating dir", e);
        }
    }

    /**
     * Throws ImplerException with our {@code message} and another exception {@code exception} messages
     *
     * @param message our extra error message
     * @param exception another exception for getting main error message
     * @throws ImplerException always when called
     */
    private static void except(final String message, Exception exception) throws ImplerException {
        throw new ImplerException(format("%s %s", message, exception.getMessage()));
    }

    /**
     * Throws ImplerException with our {@code message} message
     *
     * @param message error message
     * @throws ImplerException always when called
     */
    private static void except(final String message) throws ImplerException {
        throw new ImplerException(message);
    }

    /**
     * Convert {@code str} to Unicode
     *
     * @param str {@link String} which will convert to Unicode
     * @return Unicode view of {@code str}
     */
    private static String validator(final String str) {
        final StringBuilder builder = new StringBuilder();
        for (char ch : str.toCharArray()) {
            builder.append(ch > 127 ? format("\\u%04x", (int)ch) : ch);
        }
        return builder.toString();
    }

    /**
     * Generate implementation of {@link Class} {@code token} on {@link Path} {@code path} in jar file
     *
     * @param token type token to create implementation for.
     * @param jarFile target <var>.jar</var> file.
     * @throws ImplerException s
     */
    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        Path root = jarFile.getParent() != null ? jarFile.getParent() : jarFile;
        implement(token, root);
        compile(tryGetPath(token, root, EXTENSION), token);
        makeJar(tryGetPath(token, Path.of(""), ".class").toString().replace(File.separatorChar, '/'), jarFile, root);
    }

    /**
     * Compile java
     *
     * @param root {@link Path} where compiling
     * @param token it's {@link Class} for which you need to get {@link Path}
     * @throws ImplerException if {@link JavaCompiler#run} throw {@link NullPointerException} or error code of this method isn't 0
     */
    private static void compile(final Path root, final Class<?> token) throws ImplerException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Objects.requireNonNull(compiler, "Could not find java compiler, include tools.jar to classpath");

        final String[] args = new String[]{root.toString(), "-cp",
                root + File.pathSeparator + getClassPath(token), "-encoding", StandardCharsets.UTF_8.name()};
        try {
            final int exitCode = compiler.run(null, null, null, args);
            if (exitCode != 0) {
                except(format("Compiler exit code: %c", exitCode));
            }
        } catch (NullPointerException e) {
            except("Null element", e);
        }
    }

    /**
     * Get ClassPath of {@code token}
     *
     * @param token for getting path
     * @return {@link String} view of path
     * @throws ImplerException if error was when getting path
     */
    private static String getClassPath(final Class<?> token) throws ImplerException {
        String path = null;
        try {
            path = Path.of(token.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (URISyntaxException | SecurityException | NullPointerException e) {
            except("Bad path", e);
        }
        return path;
    }

    /**
     * Making jar file
     *
     * @param className na,e of class
     * @param jarFile {@link Path} to file
     * @param root root directory
     * @throws ImplerException in process was thrown {@link IOException} or {@link SecurityException}
     */
    private static void makeJar(String className, Path jarFile, Path root) throws ImplerException {
        try (final JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarFile))) {
            jarOutputStream.putNextEntry(new ZipEntry(className));
            Files.copy(root.resolve(className), jarOutputStream);
        } catch (IOException e) {
            except("Cannot create Jar file", e);
        } catch (SecurityException e) {
            except("Problems with security", e);
        }
    }
}
