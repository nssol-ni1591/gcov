// -*- vi: set ts=4 sts=4 sw=4 : -*-
package find;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class find_gcov {

	private Map<String, Pojo> CATELIST = new HashMap<>();
	private Pojo CACHE_CATEGORY;
	private String CACHE_UNIT = null;

	private final Pojo CATEGORY_NOT_FOUND = new Pojo();

//	private static final Pattern PATTERN1 = Pattern.compile("^(\\w*)\t([\\w\\/]\\/bin\\/(\\w\\/]*)\t([\\S ]*)\t([\\S ]*)\t([\\S ]*)\t([\\S ]*)$");
	private static final Pattern PATTERN2 = Pattern.compile("^([\\w\\/]*)\t([\\S ]*)\t([\\S ]*)\t([\\S ]*)\t([\\S ]*)\t([\\w\\.\\-]*)$");

	private static final Pattern PATTERN3 = Pattern.compile("^Function '(.*)'");
	private static final Pattern PATTERN4 = Pattern.compile("^Lines executed:([\\d\\.]+)% ([\\d]+)");
	private static final Pattern PATTERN5 = Pattern.compile("^File '(.*)'");
	private static final Pattern PATTERN6 = Pattern.compile("^([\\w]* )*([\\w\\*:&]* )?(_|std::)");

	private static final Pattern PATTERN7 = Pattern.compile("^\\s\\/\\*.*\\*\\/\\s*$");
	private static final Pattern PATTERN8 = Pattern.compile("^(\\S*)\\/\\*.*$");
	private static final Pattern PATTERN9 = Pattern.compile("^.*\\*\\/(\\S*)$");
	private static final Pattern PATTERN10 = Pattern.compile("^\\s*(#|\\/\\/).*$");
	private static final Pattern PATTERN11 = Pattern.compile("^\\s([{}])?\\s*$");

	private String function = null;

	private static void print(String msg) {
		System.out.println(msg);
	}
	private static void debug(String msg) {
//		print(msg);
		return;
	}
	private static void warning(String msg) {
		System.err.println(msg);
		return;
	}

	private boolean clearFunction() {
		function = null;
		return true;
	}

	public void make_projlist(String path) throws IOException {
		try (BufferedReader br = new BufferedReader(new FileReader(path))) {
			br.lines()
				.filter(rec -> !rec.isEmpty() && !rec.startsWith("#"))
				.map(rec -> {
					Matcher result2 = PATTERN2.matcher(rec);
					if (result2.find()) {
						return result2;
					}
					warning("mapping file format. (rec=[" + rec + "])");
					return null;
				})
				.filter(Objects::nonNull)
				.forEach(result -> {
					String unit = result.group(1);
					String[] category = new String[] { result.group(2), result.group(3), result.group(4), result.group(5) };
					String component = result.group(6);
					debug("make_projlist: unit=[" + unit + "] category=[" + String.join(" ", category) + "]");
					Pojo pojo = new Pojo(category, component);
					CATELIST.put(unit, pojo);
				});
		}
	}

	public Pojo path_to_category(String path) {
		if (CACHE_UNIT != null && path.startsWith(CACHE_UNIT)) {
//			debug("path_to_category: unit=[" + CACHE_UNIT + "] category=[" + String.join(" ", CACHE_UNIT) + "]");
			return CACHE_CATEGORY;
		}
		Optional<String> rc = CATELIST.keySet().stream()
			.filter(key -> path.contains(key + "/"))
			.findFirst();
		if (rc.isPresent()) {
			CACHE_CATEGORY = CATELIST.get(rc.get());
			CACHE_UNIT = path.substring(0, path.indexOf(rc.get()) + rc.get().length());
			debug("path_to_category: unit=[" + CACHE_UNIT + "] category=[" + CACHE_CATEGORY + "]");
			return CACHE_CATEGORY;
		}
		warning("category not found. (path=[" + path + "])");
		CACHE_UNIT = null;
		return CATEGORY_NOT_FOUND;
	}

	public void gcov_csv(String path) throws IOException {
		String cmd = "LANG=C gcov -r -p -n -f " + path + " | c++filt";
		Process gcov = Runtime.getRuntime().exec("/bin/sh");
		PrintStream pr = new PrintStream(gcov.getOutputStream());
		pr.println(cmd);
		pr.flush();
		pr.close();

		try (BufferedReader br = new BufferedReader(new InputStreamReader(gcov.getInputStream()));) {
			br.lines()	// parallel() is bad
				.filter(rec -> !rec.equals(""))
				.filter(rec -> {
					Matcher result3 = PATTERN3.matcher(rec);
					if (result3.find()) {
						function = result3.group(1);
						Matcher result = PATTERN6.matcher(function);
						if (result.find()) {
							clearFunction();
						}
						return true;
					}
					return false;
				})
				.filter(rec-> {
					Matcher result4 = PATTERN4.matcher(rec);
					if (result4.find()) {
						debug("gcov_csv: function=[" + function + "] coverage=[" + result4.group(1) + "] lines=[" + result4.group(2) + "]");
						double coverage = Double.valueOf(result4.group(1)).doubleValue();
						int lines = Integer.valueOf(result4.group(2)).intValue();

						if (function != null) {
							Pojo pojo = path_to_category(path);
							debug("gcov_csv: path=[" + path + "] category=[" + pojo + "]");
							double val = lines * coverage  / 100;
							int avail = ((int)val) + (val != ((int)val) ? 1 : 0);
							String[] array = {
								pojo.getComponent()
								, path
								, function
								, String.valueOf(coverage)
								, String.valueOf(lines)
								, String.valueOf(avail)
							};
							print(String.join("\t", pojo.getCategory()) + "\t" + String.join("\t", array));
							clearFunction();
						}
						return false;
					}
					return true;
				})
				.filter(rec -> !(PATTERN5.matcher(rec).find() && clearFunction()))
				.filter(rec -> !(rec.equals("No executable lines") && clearFunction()))
				.forEach(rec -> {
					warning("gcov format. (rec=[" + rec + "])");
				});
		}
	}

	public void file_csv(String path) throws IOException {
		Predicate<CommentState> comment = s -> s.comment();

		try (BufferedReader br = new BufferedReader(new FileReader(path));) {
			LineManager w = new LineManager(path);
			long lines = br.lines().parallel()
				.map(w::apply)
				.filter(comment.negate().and(s -> PATTERN7.matcher(s.statement()).find()).and(s -> s.trace(1)).negate())
				.filter(comment.negate().and(s -> PATTERN10.matcher(s.statement()).find()).and(s -> s.trace(2)).negate())
				.filter(comment.negate().and(s -> PATTERN11.matcher(s.statement()).find()).and(s -> s.trace(3)).negate())
				.filter(comment.negate().and(s -> {
					Matcher result8 = PATTERN8.matcher(s.statement());
					if (result8.find()) {
						s.enter();
						if (result8.group(1).isEmpty()) {
							s.trace(4);
							return true;
						}
					}
					return false;
				}).negate())
				//.filter(comment.and(s -> PATTERN9.matcher(s.statement()).find()).and(s -> s.leave()).negate())
				//.filter(comment.and(s -> s.trace()).negate())
				.filter(comment.and(s -> {
					Matcher result9 = PATTERN9.matcher(s.statement());
					if (result9.find()) {
						s.enter();
						if (result9.group(1).isEmpty()) {
							s.trace(8);
							return true;
						}
					}
					else {
						s.trace(9);
						return true;
					}
					return false;
				}).negate())
				.count();

			Pojo pojo = path_to_category(path);
			debug("file_csv: path=[" + path + "] category=[" + pojo + "]");
			String[] array = {
				pojo.getComponent()
				, path
				, "-"
				, "0.0"
				, String.valueOf(lines)
				, "0"
			};
			print(String.join("\t", pojo.getCategory()) + "\t" + String.join("\t", array));
		}
	}

	public void find_gcno(String dir) throws IOException {
		Process find = Runtime.getRuntime().exec("find " + dir + " -name *.gcno");
		try (BufferedReader br = new BufferedReader(new InputStreamReader(find.getInputStream()));) {
			br.lines().parallel()
				.forEach(rec -> {
					String gcno = rec;
					String path = gcno.substring(0, gcno.length() - 5);
					String gcda = path + ",gcda";
					String file = Stream.of(".c", ".cc", ".cpp")
						.map(ext -> path + ext)
						.filter(f -> Files.exists(Paths.get(f)))
						.findFirst()
						.orElse(null);

					debug("find_gcov: gcno=[" + gcno + "] gcda=[" + gcda + "] file=[" + file + "]");
					if (Files.exists(Paths.get(gcda))) {
						if (file == null) {
							file = path + ".o";
						}

						try {
							gcov_csv(file);
						}
						catch (IOException ex) {
							throw new RuntimeException(ex);
						}
					}
					else if (file != null) {
						try {
							file_csv(file);
						}
						catch (IOException ex) {
							throw new RuntimeException(ex);
						}
					}
				});
		}
	}

	public find_gcov(String[] argv) {
		try {
			make_projlist(argv[0]);
			print("システムブロック\tサブシステム\tサービス群\tサービス\tコンポーネント\tファイルパス\t関数\tcoverage\t対象行数\t実行行数");
			for (int ix = 1; ix < argv.length; ix++) {
				find_gcno(argv[ix]);
			}
		}
		catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public static void main(String[] argv) {
		if (argv.length < 2) {
			print("Usage: java find_gcov projfile dir ...");
			System.exit(1);
		}
		new find_gcov(argv);
	}

	public class LineManager {

		private String name;
		private long lines;
		private boolean comment;

		public LineManager(String path) {
			this.name = Paths.get(path).getFileName().toString();
			this.lines = 0;
			this.comment = false;
		}

		public CommentState apply(String arg) {
			return new CommentState(this, nextLines(), arg);
		}
		public synchronized long nextLines() {
			return ++lines;
		}
	}

	public class CommentState extends AbstractMap.SimpleImmutableEntry<Long, String> {

		private static final long serialVersionUID = 1L;
		private LineManager parent;

		public CommentState(LineManager parent, long key, String value) {
			super(key, value);
			this.parent = parent;
		}

		public String statement() {
			return getValue();
		}

		public boolean trace(int id) {
			debug("(" + id + ")" + parent.name + ":" + getKey() + ":" + getValue());
			return true;
		}
		public boolean enter() {
			parent.comment = true;
			return true;
		}
		public boolean leave() {
			parent.comment = false;
			return true;
		}
		public boolean comment() {
			return parent.comment;
		}
	}

	public class Pojo {

		private final String[] category;
		private final String component;

		public Pojo() {
			this.category = new String[] { "-", "-", "-", "-" };
			this.component = "-";
		}
		public Pojo(String[] category, String component) {
			this.category = category;
			this.component = component;
		}

		public String[] getCategory() {
			return category;
		}
		public String getComponent() {
			return component;
		}

		public boolean equals(String[] category) {
			return this.category[0] == category[0]
				&& this.category[1] == category[1]
				&& this.category[2] == category[2]
				&& this.category[3] == category[3]
			;
		}
		public String toString() {
			return String.join(" ", category) + " (" + component + ")";
		}
	}
}
