import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.lang.model.element.Modifier;
import javax.tools.*;

/** Read-only inventory of library declarations and existing JavaDoc using the JDK parser. */
public final class LibraryDocInventory {
    private static final List<String> items = new ArrayList<String>();

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args[0]);
        List<java.io.File> files = new ArrayList<java.io.File>();
        for (String module : Arrays.asList("library", "client", "server")) {
            Path sourceRoot = root.resolve(module + "/src/main/java");
            try (java.util.stream.Stream<Path> stream = Files.walk(sourceRoot)) {
                stream.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> module.equals("library") || path.getFileName().toString().contains("Library"))
                        .forEach(path -> files.add(path.toFile()));
            }
        }
        files.sort(Comparator.comparing(java.io.File::getAbsolutePath));
        List<java.io.File> selected = args.length > 2
                ? files.subList(Math.min(files.size(), Integer.parseInt(args[1])),
                        Math.min(files.size(), Integer.parseInt(args[2]))) : files;
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, manager, null,
                    Arrays.asList("-proc:none", "-encoding", "UTF-8"), null, manager.getJavaFileObjectsFromFiles(selected));
            final DocTrees docs = DocTrees.instance(task);
            final SourcePositions positions = docs.getSourcePositions();
            for (CompilationUnitTree unit : task.parse()) {
                final String path = Paths.get(unit.getSourceFile().toUri()).toString().replace('\\', '/');
                new TreePathScanner<Void, Void>() {
                    @Override public Void visitClass(ClassTree tree, Void ignored) {
                        if (tree.getSimpleName().length() == 0) return null;
                        emit("class", tree.getSimpleName().toString(), tree, "", Collections.emptyList(), Collections.emptyList());
                        return super.visitClass(tree, ignored);
                    }
                    @Override public Void visitMethod(MethodTree tree, Void ignored) {
                        Tree parent = getCurrentPath().getParentPath().getLeaf();
                        if (!(parent instanceof ClassTree)) return super.visitMethod(tree, ignored);
                        String name = tree.getName().toString();
                        if (name.equals("<init>")) name = ((ClassTree) parent).getSimpleName().toString();
                        List<String> params = new ArrayList<String>();
                        for (VariableTree param : tree.getParameters()) {
                            params.add("{\"name\":" + q(param.getName().toString())
                                    + ",\"type\":" + q(param.getType().toString()) + "}");
                        }
                        for (TypeParameterTree param : tree.getTypeParameters()) {
                            params.add("{\"name\":" + q("<" + param.getName() + ">") + ",\"type\":\"type parameter\"}");
                        }
                        List<String> thrown = new ArrayList<String>();
                        for (ExpressionTree type : tree.getThrows()) thrown.add(q(type.toString()));
                        emit("method", name, tree, tree.getReturnType() == null ? "" : tree.getReturnType().toString(),
                                params, thrown);
                        return super.visitMethod(tree, ignored);
                    }
                    @Override public Void visitVariable(VariableTree tree, Void ignored) {
                        Tree parent = getCurrentPath().getParentPath().getLeaf();
                        if (parent instanceof ClassTree) {
                            emit("field", tree.getName().toString(), tree,
                                    tree.getType() == null ? "" : tree.getType().toString(),
                                    Collections.emptyList(), Collections.emptyList());
                        }
                        return super.visitVariable(tree, ignored);
                    }
                    private void emit(String kind, String name, Tree tree, String type,
                            List<String> params, List<String> thrown) {
                        long offset = positions.getStartPosition(unit, tree);
                        String comment = docs.getDocComment(getCurrentPath());
                        items.add("{\"path\":" + q(path) + ",\"kind\":" + q(kind)
                                + ",\"name\":" + q(name) + ",\"type\":" + q(type)
                                + ",\"start\":" + offset + ",\"line\":" + unit.getLineMap().getLineNumber(offset)
                                + ",\"comment\":" + q(comment == null ? "" : comment)
                                + ",\"params\":[" + String.join(",", params) + "],\"throws\":["
                                + String.join(",", thrown) + "]}");
                    }
                }.scan(unit, null);
            }
        }
        System.out.println("[" + String.join(",", items) + "]");
    }

    private static String q(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }
}
