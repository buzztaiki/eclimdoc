import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.*;

import org.eclim.annotation.Command;

import com.sun.mirror.apt.*;
import com.sun.mirror.declaration.*;

public class EclimDoc implements AnnotationProcessorFactory {
    static final String ANNOTATION = "org.eclim.annotation.Command";
    static final String OPT_FORMAT = "-Aformat";
    public Collection<String> supportedAnnotationTypes() {
        return Arrays.asList(ANNOTATION);
    }
    public Collection<String> supportedOptions() {
        return Arrays.asList(OPT_FORMAT);
    }
    public AnnotationProcessor getProcessorFor(Set<AnnotationTypeDeclaration> atds, AnnotationProcessorEnvironment env) {
        if (atds.contains(env.getTypeDeclaration(ANNOTATION))) {
            return new EclimDocProcessor(env);
        } else {
            return AnnotationProcessors.NO_OP;
        }
    }
    static String getPackage(TypeDeclaration td) {
        return td.getPackage().getQualifiedName();
    }
    static Command getCommand(TypeDeclaration td) {
        return td.getAnnotation(Command.class);
    }
    static String trimDoc(String doc) {
        StringBuilder sb = new StringBuilder();
        for (String l : doc.replaceAll("\r\n", "\n").split("\n")) {
            l = l.replaceAll("^ ", "");
            if (l.startsWith("@")) {
                break;
            }
            sb.append(l + "\n");
        }
        return sb.toString();
    }
    static List<String> parseCommandOptions(String optsStr) {
        optsStr = optsStr.trim();
        if (optsStr.length() <= 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(optsStr.split(","));
    }
}

class EclimDocProcessor implements AnnotationProcessor {
    AnnotationProcessorEnvironment env;
    EclimDocProcessor(AnnotationProcessorEnvironment env) {
        this.env = env;
    }
    public void process() {
        List<TypeDeclaration> tds = new ArrayList<TypeDeclaration>();
        for (TypeDeclaration td : env.getSpecifiedTypeDeclarations()) {
            Command command = EclimDoc.getCommand(td);
            if (command == null) {
                continue;
            }
            tds.add(td);
        }
        Printer p = getPrinter();
        p.startPkgs(System.out);
        for (Entry<String, List<TypeDeclaration>> e : classify(tds).entrySet()) {
            p.printPkg(System.out, e.getKey(), e.getValue());
        }
        p.endPkgs(System.out);
        sort(tds);
        p.startDescs(System.out);
        for (TypeDeclaration td : tds) {
            p.printDesc(System.out, td);
        }
        p.endDescs(System.out);
    }
    Map<String, String> getOptions() {
        Map<String, String> opts = new HashMap<String, String>();
        for (Entry<String, String> e : env.getOptions().entrySet()) {
            String key = e.getKey();
            if (!key.startsWith("-A")) {
                continue;
            }
            String[] a = key.split("=");
            assert a.length > 0;
            if (a.length == 1) {
                opts.put(a[0], e.getValue());
            } else {
                opts.put(a[0], a[1]);
            }
        }
        return opts;
    }
    Printer getPrinter() {
        Map<String, String> opts = getOptions();
        if ("html".equals(opts.get(EclimDoc.OPT_FORMAT))) {
            return new HtmlPrinter();
        }
        return new SimplePrinter();
    }
    Map<String, List<TypeDeclaration>> classify(List<TypeDeclaration> tds) {
        Map<String, List<TypeDeclaration>> map = new HashMap<String, List<TypeDeclaration>>();
        for (TypeDeclaration td : tds) {
            String longPkg = EclimDoc.getPackage(td);
            Matcher m = Pattern.compile("^org.eclim.plugin.([^.]+)").matcher(longPkg);
            String pkg = (m.find()) ? m.group(1) : longPkg;
            List<TypeDeclaration> l = map.get(pkg);
            if (l == null) {
                map.put(pkg, l = new ArrayList<TypeDeclaration>());
            }
            l.add(td);
        }
        for (List<TypeDeclaration> l : map.values()) {
            sort(l);
        }
        return map;
    }
    void sort(List<TypeDeclaration> tds) {
        Collections.sort(tds, new Comparator<TypeDeclaration>() {
            public int compare(TypeDeclaration o1, TypeDeclaration o2) {
                int cmp = EclimDoc.getPackage(o1).compareTo(EclimDoc.getPackage(o2));
                if (cmp != 0) {
                    return cmp;
                }
                return EclimDoc.getCommand(o1).name().compareTo(EclimDoc.getCommand(o2).name());
            }
        });
    }
}

interface Printer {
    void startPkgs(PrintStream out);
    void endPkgs(PrintStream out);
    void printPkg(PrintStream out, String pkg, List<TypeDeclaration> tds);
    void startDescs(PrintStream out);
    void endDescs(PrintStream out);
    void printDesc(PrintStream out, TypeDeclaration td);
}

class SimplePrinter implements Printer {
    public void startPkgs(PrintStream out) {}
    public void endPkgs(PrintStream out) {}
    public void printPkg(PrintStream out, String pkg, List<TypeDeclaration> tds) {
        out.println(pkg);
        for (TypeDeclaration td : tds) {
            out.println(" " + EclimDoc.getCommand(td).name());
        }
    }
    public void startDescs(PrintStream out) {}
    public void endDescs(PrintStream out) {}
    public void printDesc(PrintStream out, TypeDeclaration td) {
        Command command = EclimDoc.getCommand(td);
        out.println("================");
        out.println(command.name());
        for (String opt : EclimDoc.parseCommandOptions(command.options())) {
            out.println("  " + opt);
        }
        out.println("----------------");
        out.print(EclimDoc.trimDoc(td.getDocComment()));
        out.println(td.getQualifiedName());
        out.println(td.getPosition().file());
        out.println();
    }
}

class HtmlPrinter implements Printer {
    public void startPkgs(PrintStream out) {
        out.println("<dl>");
    }
    public void endPkgs(PrintStream out) {
        out.println("</dl>");
    }
    public void printPkg(PrintStream out, String pkg, List<TypeDeclaration> tds) {
        out.println("<dt>" + pkg + "</dt>");
        out.println("<dd><ul>");
        for (TypeDeclaration td : tds) {
            String name = EclimDoc.getCommand(td).name();
            out.format("<li id='pkg_%s'><a href='#%s'>%s</a>", name, name, name).println();
        }
        out.println("</ul></dd>");
    }
    public void startDescs(PrintStream out) {}
    public void endDescs(PrintStream out) {}
    public void printDesc(PrintStream out, TypeDeclaration td) {
        Command command = EclimDoc.getCommand(td);
        String name = command.name();
        out.format("<h3 id='%s'><a href='#pkg_%s'>%s</a></h3>", name, name, name).println();
        out.println("<p>" + EclimDoc.trimDoc(td.getDocComment()) + "</p>");
        out.println("<ul>");
        for (String opt : EclimDoc.parseCommandOptions(command.options())) {
            out.println("<li>" + opt);
        }
        out.println("</ul>");
        out.println("<ul>");
        out.println("<li>" + td.getQualifiedName());
        out.println("<li>" + td.getPosition().file());
        out.println("</ul>");
    }
}