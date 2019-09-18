// -*- vi: set ts=4 sts=4 sw=4 : -*-

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class make_mapping {

	class category_component {

		private final String[] category;
		private final String component;
		private String unit;

		public category_component(String[] category, String component) {
			this.category = category;
			this.component = component;
		}
		public String categories_to_string() {
			return String.join(" ", category);
		}
		public boolean equals_categories(String[] category) {
			boolean rc = this.category[0].equals(category[0])
				&& this.category[1].equals(category[1])
				&& this.category[2].equals(category[2])
				&& this.category[3].equals(category[3])
				;
			return rc;
		}
		public String get_component() {
			return this.component;
		}
		public String[] get_category() {
			return this.category;
		}
		public String get_unit() {
			return this.unit;
		}
		public void set_unit(String unit) {
			this.unit = unit;
		}
	}

	private static final Map<String, String> STATIC_MAPPING = new HashMap<String, String>() {
		{
			put("...", "...");
		}
	};

	private final String[] SKIP_COMPONENTS = {
		"...",
	};

	private static final Pattern PATTERN1 = Pattern.compile("^([\\w\\/]*)\\/src\\/(.*)$");
	private static final Pattern PATTERN2 = Pattern.compile("^([\\S\\ ]*)\\t([\\S\\ ]*)\\t([\\S\\ ]*)\\t([\\S\\ ]*)\\t([\\w\\.-]*)\t*$");
	private static final Pattern PATTERN3 = Pattern.compile("^\\S*\\.(sh|adf|sts|ear|jar)$");

	private static void print(String format, Object...args) {
		System.out.format(format + "\n", args);
	}
	private static void debug(String format, Object...args) {
		print(format, args);
	}
	private static void warning(String format, Object...args) {
		System.err.format(format, args);
	}

	private boolean skip_component(String component) {
		Matcher result3 = PATTERN3.matcher(component);
		if (result3.find()) {
			debug("skip_component: excluded component=[%s]", component);
			return false;
		}
		return Arrays.stream(SKIP_COMPONENTS)
			.filter(component::equals)
			.findFirst()
			.isPresent();
	}

	public boolean check_duplicate_unit(Map<String, category_component> list, String unit, String component, String[] category) {
//		debug("check_duplicate_unit: unit=[%s] component=[%s] list.get=[%s]", unit, component, list.get(unit));
		if (list.get(unit) != null) {
			if (component.equals(list.get(unit).get_component())) {
				if (!list.get(unit).equals_categories(category)) {
					String p1 = String.join(" ", category);
					String p2 = list.get(unit).categories_to_string();
					warning("duplicate components. (component=[%s] category=[%s] => [%s]", component, p1, p2);
				}
				return false;
			}
			if (!list.get(unit).equals_categories(category)) {
				String p1 = list.get(unit).get_component();
				String p2 = list.get(unit).categories_to_string();
				warning("different category in same unit. (unit=[%s] comp/cate=[%s]/[%s] => [%s]/[%s]"
					, unit, component, String.join(" ", category), p1, p2);
			}
			return false;
		}
		return true;
	}

	public boolean check_parent_unit(Map<String, category_component> list, String unit, String component, String[] category) {
		Stream.Builder<String> builder = Stream.builder();
		Path d = Paths.get(unit).getParent();
		while (d != null) {
			builder.add(d.toString());
			d = d.getParent();
		}
		return builder.build()
			.map(list::get)
			.filter(Objects::nonNull)
			.filter(c -> {
				if (!c.equals_categories(category)) {
					String p1 = String.join(" ", category);
					warning("parent unit exists. (component=[%s] unit=[%s] d=[%s] category=[%s]", component, c.get_unit(), c.get_unit(), p1);
				}
				else {
//					debug("check_parent_unit: skip unit=[%s] component=[%s] d=[%s]", unit, component, d);
				}
				return true;
			})
			.findFirst()
			.isPresent();
	}

	public boolean check_child_unit(Map<String, category_component> list, String unit, String component, String[] category) {
		return (list.keySet().stream()
			.filter(u -> u.startsWith(unit + "/"))
			.filter(u -> {
				if (!list.get(u).equals_categories(category)) {
					String p1 = String.join(" ", category);
					warning("child unit exists. (commponent=[%s] unit=[%s] child=[%s] category=[%s]", component, unit, u, p1);
					return false;
				}
				debug("check_child_unit: delete component=[%s] unit=[%s] child=[%s (%s)]", component, unit, u, list.get(u).get_component());
				list.remove(u);
				return true;
			})
			.count()) == 0;	//全ての要素を処理したい
	}

	public String find_unit(String dir, String component) throws IOException {
		String unit = STATIC_MAPPING.get(component);
		if (unit != null) {
			if (!Files.exists(Paths.get(dir + unit))) {
				print("Error: unit not exists. (unit=[%s] path=[%s]", unit, dir + unit);
				System.exit(1);
			}
			return unit;
		}
		List<String> paths = new ArrayList<>();
		Process find = Runtime.getRuntime().exec("find " + dir + " -name " + component + " -type f");
		try (BufferedReader br = new BufferedReader(new InputStreamReader(find.getInputStream()));) {
			br.lines()
				.forEach(path -> {
					paths.add(path.substring(dir.length()));
				});
		}
		if (paths.isEmpty()) {
			return null;
		}
		List<String> srcdirs = new ArrayList<>();
		if (paths.size() > 1) {
			paths.stream()
				.filter(path -> !path.contains("/bin/"))
				.filter(path -> !path.contains("/doc/"))
				.filter(path -> !(path.contains("/lib/") && component.startsWith("lib") && component.endsWith(".so")))
				.forEach(srcdirs::add);
			if (srcdirs.size() > 1) {
				warning("dupulicate paths. (component=[%s] paths=[%s]", component, String.join(" ", srcdirs));
			}
			else if (srcdirs.size() < 1) {
				srcdirs = paths;
			}
		}
		else {
			srcdirs = paths;
		}

		String name = component;
		if (component.startsWith("lib") && component.endsWith(".so")) {
			name = component.substring(3, component.length() - 3);
		}

		Path srcdir = Paths.get(srcdirs.get(0)).getParent();
		if (name.equals(srcdir.getFileName().toString())) {
			unit = srcdir.toString();
		}
		else if (name.equals("Dss" + srcdir.getFileName().toString())) {
			unit = srcdir.toString();
		}
		else {
			int index = srcdir.toString().indexOf("src");
			if (index > 0) {
				unit = srcdir.toString().substring(0, index - 1);
			}
			else {
				unit = srcdir.toString();
			}
		}

		if (!Files.exists(Paths.get(dir + unit))) {
			print("Error: unit not exists. (unit=[%s] path=[%s])", unit, dir + unit);
			System.exit(1);
		}
//		debug("find_unit: dir=[%s] component=[%s] unit=[%s]", dir, component, unit);
		return unit;
	}

	public Map<String, category_component> make_complist(String path, String topdir) throws IOException {
		final Map<String, category_component> list = new ConcurrentHashMap<>();
		try (Stream<String> stream = Files.lines(Paths.get(path), Charset.forName("ms932"));) {
			stream.parallel()
				.map(rec -> {
//					debug(make_complist: rec=[%s]", rec");
					Matcher result2 = PATTERN2.matcher(rec);
					if (result2.find()) {
						String[] category = new String[] { result2.group(1), result2.group(2), result2.group(3), result2.group(4) };
						String component = result2.group(5);
						return new category_component(category, component);
					}
					warning("complist file format. (rec=[%s])", rec);
					return null;
				})
				.filter(Objects::nonNull)
				.filter(cc -> !(cc.get_component() == null || cc.get_component().equals("0") || cc.get_component().equals("-")))
				.filter(cc -> !skip_component(cc.get_component()))
				.map(cc -> {
					try {
						String unit = find_unit(topdir, cc.get_component());
						if (unit == null) {
							warning("component not found. (component=[%s] category=[%s]", cc.get_component(), cc.categories_to_string());
						}
						cc.set_unit(unit);
					}
					catch (IOException ex) {
						throw new RuntimeException(ex);
					}
					return cc;
				})
				.filter(cc -> cc.get_unit() != null)
				.filter(cc -> check_duplicate_unit(list, cc.get_unit(), cc.get_component(), cc.get_category()))
				.filter(cc -> check_parent_unit(list, cc.get_unit(), cc.get_component(), cc.get_category()))
				.filter(cc -> check_child_unit(list, cc.get_unit(), cc.get_component(), cc.get_category()))
				.forEach(cc -> {
					debug("make_complist: add unit=[%s] component=[%s] category=[%s]", cc.get_unit(), cc.get_component(), cc.categories_to_string());
					list.put(cc.get_unit(), cc);
				});
		}
		return list;
	}

	public make_mapping(String[] argv) {
		String path = argv[1];
		if (path.charAt(path.length() - 1) != '/') {
			path += "/";
		}
		try {
			Map<String, category_component> list = make_complist(argv[0], path);
			print("コンパイル単位\tシステムブロック\tサブシステム\tサービス群\tサービス\tコンポーネント");
			list.keySet().stream()
				.map(unit -> String.format("%s\t%s\t%s", unit, String.join("\t", list.get(unit).get_category()), list.get(unit).get_component()))
				.forEach(make_mapping::print);
		}
		catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	public static void main(String[] argv) {
		if (argv.length != 2) {
			print("usage: java %s complist topdir\n", "make_mapping");
			System.exit(1);
		}
		new make_mapping(argv);
	}
}
