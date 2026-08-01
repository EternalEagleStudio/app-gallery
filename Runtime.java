package org.eclipse.jdt.Android;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* 伪 java.lang.Runtime 类，为 ECJ 提供运行时版本信息。
* <p>
* 在 Android 平台上，java.lang.Runtime 没有 version() 方法，
* 因此通过此类模拟需要的 JDK 版本查询功能。
* <p>
* 本类与 java.lang.Runtime 及 java.lang.Runtime.Version 的方法签名保持兼容，
* 使依赖 Runtime.version() 的代码能够在 Android 环境下编译和运行。
* <p>
* 支持 Java 9+ 版本号格式：
* <pre>feature[.interim[.update[.patch]]][-pre][+build-info]</pre>
* 例如：{@code "21"}, {@code "17.0.2+8"}, {@code "11.0.1-ea"}
* <p>
* 本实现对齐 Java 21 的 Runtime.Version API 行为，同时保持对旧版 JDK 格式的兼容。
* 所有实现严格使用 Java 8 API，确保在 Android API 34 及 AIDE 中可编译。
*
* @since 1.0
*/
public final class Runtime {
	
	/**
	* 默认的 feature（主版本）版本号。
	* 模拟 JDK 21 的运行时环境。
	*/
	static final int DEFAULT_FEATURE_VERSION = 21;
	
	private static Version version;
	
	/**
	* 私有构造方法，防止实例化。
	*/
	private Runtime() {
		throw new AssertionError("Runtime class cannot be instantiated");
	}
	
	/**
	* 模拟 java.lang.Runtime.version()，返回一个 Version 对象。
	* <p>
	* 默认返回 feature 版本为 {@value #DEFAULT_FEATURE_VERSION} 的版本对象。
	*
	* @return 当前模拟的运行时版本
	*/
	public static Version version() {
		Version v = version;
		if (v == null) {
			List<Integer> ver = new ArrayList<Integer>();
			ver.add(DEFAULT_FEATURE_VERSION);
			v = new Version(Collections.unmodifiableList(ver),
			Optional.<String>empty(),
			Optional.<Integer>empty(),
			Optional.<String>empty());
			version = v;
		}
		return v;
	}
	
	/**
	* 与 java.lang.Runtime$Version 方法签名兼容的自定义版本类。
	* <p>
	* 版本号由以下部分组成：
	* <ul>
	*   <li><b>feature</b>（主版本号）- 如 Java 21 中的 21</li>
	*   <li><b>interim</b>（临时版本号）- 通常为 0</li>
	*   <li><b>update</b>（更新版本号）- 如 17.0.2 中的 2</li>
	*   <li><b>patch</b>（补丁版本号）- 紧急补丁版本</li>
	*   <li><b>pre</b>（预发布标识）- 如 "ea"（早期访问版）</li>
	*   <li><b>build</b>（构建号）- 如 "+8"</li>
	*   <li><b>optional</b>（可选信息）- 附加版本信息</li>
	* </ul>
	*/
	public static final class Version implements Comparable<Version> {
		
		private final List<Integer> version;
		private final Optional<String> pre;
		private final Optional<Integer> build;
		private final Optional<String> optional;
		
		/**
		* 静态工厂方法：解析版本字符串，仿照 java.lang.Runtime.Version.parse()
		* <p>
		* 支持的格式示例：
		* <ul>
		*   <li>{@code "21"} — 仅主版本号</li>
		*   <li>{@code "17.0"} — 主版本 + 临时版本</li>
		*   <li>{@code "11.0.2"} — 主版本 + 临时版本 + 更新版本</li>
		*   <li>{@code "1.8.0_291"} — 兼容旧版 JDK 格式（自动转为 8.0.291）</li>
		*   <li>{@code "17-ea"} — 带预发布标识</li>
		*   <li>{@code "17+35"} — 带构建号</li>
		*   <li>{@code "17.0.2+8-123"} — 完整格式</li>
		*   <li>{@code "21.0.1+12-19"} — 含可选信息</li>
		* </ul>
		*
		* @param s 版本字符串，可为 null 或空字符串
		* @return 解析后的 Version 对象；如果解析失败则返回默认版本（feature=21）
		*/
		public static Version parse(String s) {
			if (s == null || s.trim().isEmpty()) {
				return defaultVersion();
			}
			
			String str = s.trim();
			
			// 兼容旧版格式如 "1.8.0_291"，去掉开头的 "1." 并将下划线转为点号
			if (str.startsWith("1.") && str.length() > 2) {
				char c = str.charAt(2);
				if (c >= '0' && c <= '9') {
					str = str.substring(2);
				}
			}
			if (str.indexOf('_') >= 0) {
				str = str.replace('_', '.');
			}
			
			// 快速路径：纯数字版本号
			if (isSimpleNumber(str)) {
				List<Integer> ver = new ArrayList<Integer>();
				ver.add(Integer.parseInt(str));
				return new Version(Collections.unmodifiableList(ver),
				Optional.<String>empty(),
				Optional.<Integer>empty(),
				Optional.<String>empty());
			}
			
			Matcher m = VersionPattern.VSTR_PATTERN.matcher(str);
			if (!m.matches()) {
				return defaultVersion();
			}
			
			String[] split = m.group(VersionPattern.VNUM_GROUP).split("\\.");
			List<Integer> versionList = new ArrayList<Integer>();
			for (String part : split) {
				versionList.add(Integer.parseInt(part));
			}
			
			Optional<String> pre = Optional.ofNullable(m.group(VersionPattern.PRE_GROUP));
			
			String b = m.group(VersionPattern.BUILD_GROUP);
			Optional<Integer> build = (b == null)
			? Optional.<Integer>empty()
			: Optional.of(Integer.parseInt(b));
			
			Optional<String> optional = Optional.ofNullable(m.group(VersionPattern.OPT_GROUP));
			
			// 验证 '+' 的合法性（与 Java 21 一致）
			if (!build.isPresent()) {
				if (m.group(VersionPattern.PLUS_GROUP) != null) {
					if (optional.isPresent()) {
						if (pre.isPresent()) {
							return defaultVersion();
						}
						} else {
						return defaultVersion();
					}
					} else {
					if (optional.isPresent() && !pre.isPresent()) {
						return defaultVersion();
					}
				}
			}
			
			return new Version(Collections.unmodifiableList(versionList), pre, build, optional);
		}
		
		private static boolean isSimpleNumber(String s) {
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				char lowerBound = (i > 0) ? '0' : '1';
				if (c < lowerBound || c > '9') {
					return false;
				}
			}
			return true;
		}
		
		private static Version defaultVersion() {
			List<Integer> ver = new ArrayList<Integer>();
			ver.add(DEFAULT_FEATURE_VERSION);
			return new Version(Collections.unmodifiableList(ver),
			Optional.<String>empty(),
			Optional.<Integer>empty(),
			Optional.<String>empty());
		}
		
		/*
		* List of version number components passed to this constructor MUST
		* be at least unmodifiable (ideally immutable).
		*/
		private Version(List<Integer> version, Optional<String> pre,
		Optional<Integer> build, Optional<String> optional) {
			this.version = version;
			this.pre = pre;
			this.build = build;
			this.optional = optional;
		}
		
		/**
		* 构造默认版本（feature = {@value Runtime#DEFAULT_FEATURE_VERSION}）
		*/
		public Version() {
			this(defaultVersion().version,
			Optional.<String>empty(),
			Optional.<Integer>empty(),
			Optional.<String>empty());
		}
		
		/**
		* 构造指定 feature 版本的版本对象。
		*
		* @param feature 主版本号
		*/
		public Version(int feature) {
			this(normalizeVersionList(feature, 0, 0, 0),
			Optional.<String>empty(),
			Optional.<Integer>empty(),
			Optional.<String>empty());
		}
		
		/**
		* 构造指定 feature 和 interim 版本的版本对象。
		*
		* @param feature 主版本号
		* @param interim 临时版本号
		*/
		public Version(int feature, int interim) {
			this(normalizeVersionList(feature, interim, 0, 0),
			Optional.<String>empty(),
			Optional.<Integer>empty(),
			Optional.<String>empty());
		}
		
		/**
		* 构造指定 feature、interim、update 和 patch 的版本对象。
		*
		* @param feature 主版本号
		* @param interim 临时版本号
		* @param update  更新版本号
		* @param patch   补丁版本号
		*/
		public Version(int feature, int interim, int update, int patch) {
			this(normalizeVersionList(feature, interim, update, patch),
			Optional.<String>empty(),
			Optional.<Integer>empty(),
			Optional.<String>empty());
		}
		
		/**
		* 完整构造方法。
		*
		* @param feature  主版本号
		* @param interim  临时版本号
		* @param update   更新版本号
		* @param patch    补丁版本号
		* @param build    构建号
		* @param pre      预发布标识
		* @param optional 可选版本信息
		*/
		public Version(int feature, int interim, int update, int patch,
		String build, String pre, String optional) {
			this(normalizeVersionList(feature, interim, update, patch),
			toOptionalString(pre),
			toOptionalBuild(build),
			toOptionalString(optional));
		}
		
		private static List<Integer> normalizeVersionList(int feature, int interim, int update, int patch) {
			List<Integer> list = new ArrayList<Integer>();
			list.add(Math.max(0, feature));
			if (interim != 0 || update != 0 || patch != 0) {
				list.add(Math.max(0, interim));
				if (update != 0 || patch != 0) {
					list.add(Math.max(0, update));
					if (patch != 0) {
						list.add(Math.max(0, patch));
					}
				}
			}
			return Collections.unmodifiableList(list);
		}
		
		private static Optional<String> toOptionalString(String s) {
			return (s == null || s.isEmpty() || "unknown".equals(s))
			? Optional.<String>empty()
			: Optional.of(s);
		}
		
		private static Optional<Integer> toOptionalBuild(String s) {
			if (s == null || s.isEmpty() || "unknown".equals(s)) {
				return Optional.<Integer>empty();
			}
			try {
				return Optional.of(Integer.parseInt(s));
				} catch (NumberFormatException e) {
				return Optional.<Integer>empty();
			}
		}
		
		/**
		* 返回 feature（主版本）号。
		* <p>
		* 在 Java 9+ 中，feature 替代了旧版的 major 版本号概念。
		*
		* @return 主版本号
		*/
		public int feature() {
			return version.get(0);
		}
		
		/**
		* 返回 interim（临时）版本号。
		*
		* @return 临时版本号，若不存在则返回 0
		*/
		public int interim() {
			return version.size() > 1 ? version.get(1) : 0;
		}
		
		/**
		* 返回 update（更新）版本号。
		*
		* @return 更新版本号，若不存在则返回 0
		*/
		public int update() {
			return version.size() > 2 ? version.get(2) : 0;
		}
		
		/**
		* 返回 patch（补丁）版本号。
		*
		* @return 补丁版本号，若不存在则返回 0
		*/
		public int patch() {
			return version.size() > 3 ? version.get(3) : 0;
		}
		
		/**
		* 返回 major 版本号。
		* <p>
		* 与 {@link #feature()} 等价，为兼容旧版 API 保留。
		*
		* @return 主版本号
		* @deprecated 自 Java 10 起，请使用 {@link #feature()}
		*/
		@Deprecated
		public int major() {
			return feature();
		}
		
		/**
		* 返回 minor 版本号。
		* <p>
		* 与 {@link #interim()} 等价，为兼容旧版 API 保留。
		*
		* @return 临时版本号
		* @deprecated 自 Java 10 起，请使用 {@link #interim()}
		*/
		@Deprecated
		public int minor() {
			return interim();
		}
		
		/**
		* 返回 security 版本号。
		* <p>
		* 与 {@link #update()} 等价，为兼容旧版 API 保留。
		*
		* @return 更新版本号
		* @deprecated 自 Java 10 起，请使用 {@link #update()}
		*/
		@Deprecated
		public int security() {
			return update();
		}
		
		/**
		* 返回版本号整数列表。
		*
		* @return 不可修改的版本号列表
		*/
		public List<Integer> version() {
			return version;
		}
		
		/**
		* 返回预发布版本标识。
		*
		* @return 预发布标识的 Optional，如 "ea"；若无则返回 Optional.empty()
		*/
		public Optional<String> pre() {
			return pre;
		}
		
		/**
		* 返回构建号。
		*
		* @return 构建号的 Optional；若未知则返回 Optional.empty()
		*/
		public Optional<Integer> build() {
			return build;
		}
		
		/**
		* 返回可选版本信息。
		* <p>
		* 与 Java 9+ Runtime.Version 的 optional() 方法签名兼容。
		*
		* @return 可选版本信息的 Optional，若无则返回 Optional.empty()
		*/
		public Optional<String> optional() {
			return optional;
		}
		
		/**
		* 判断是否为预发布版本。
		*
		* @return 如果存在预发布标识则返回 true
		*/
		public boolean isPreRelease() {
			return pre.isPresent();
		}
		
		/**
		* 判断当前版本是否大于等于指定版本。
		*
		* @param other 要比较的版本
		* @return 如果当前版本大于等于 other 则返回 true
		*/
		public boolean isAtLeast(Version other) {
			return this.compareTo(other) >= 0;
		}
		
		/**
		* 比较两个版本。
		* <p>
		* 比较顺序：版本号 &gt; pre-release &gt; build &gt; optional
		*
		* @param obj 要比较的版本
		* @return 负数、零或正数
		* @throws NullPointerException 如果 obj 为 null
		*/
		@Override
		public int compareTo(Version obj) {
			return compare(obj, false);
		}
		
		/**
		* 比较两个版本，忽略 optional 信息。
		*
		* @param obj 要比较的版本
		* @return 负数、零或正数
		* @throws NullPointerException 如果 obj 为 null
		*/
		public int compareToIgnoreOptional(Version obj) {
			return compare(obj, true);
		}
		
		private int compare(Version obj, boolean ignoreOpt) {
			if (obj == null) {
				throw new NullPointerException();
			}
			
			int ret = compareVersion(obj);
			if (ret != 0) return ret;
			
			ret = comparePre(obj);
			if (ret != 0) return ret;
			
			ret = compareBuild(obj);
			if (ret != 0) return ret;
			
			if (!ignoreOpt) {
				return compareOptional(obj);
			}
			
			return 0;
		}
		
		private int compareVersion(Version obj) {
			int size = version.size();
			int oSize = obj.version.size();
			int min = Math.min(size, oSize);
			for (int i = 0; i < min; i++) {
				int val = version.get(i);
				int oVal = obj.version.get(i);
				if (val != oVal) {
					return val - oVal;
				}
			}
			return size - oSize;
		}
		
		private int comparePre(Version obj) {
			if (!pre.isPresent()) {
				if (obj.pre.isPresent()) {
					return 1;
				}
				} else {
				if (!obj.pre.isPresent()) {
					return -1;
				}
				String val = pre.get();
				String oVal = obj.pre.get();
				if (val.matches("\\d+")) {
					return obj.pre.get().matches("\\d+")
					? new BigInteger(val).compareTo(new BigInteger(oVal))
					: -1;
					} else {
					return obj.pre.get().matches("\\d+")
					? 1
					: val.compareTo(oVal);
				}
			}
			return 0;
		}
		
		private int compareBuild(Version obj) {
			if (obj.build.isPresent()) {
				return build.isPresent()
				? build.get().compareTo(obj.build.get())
				: -1;
				} else if (build.isPresent()) {
				return 1;
			}
			return 0;
		}
		
		private int compareOptional(Version obj) {
			if (!optional.isPresent()) {
				if (obj.optional.isPresent()) {
					return -1;
				}
				} else {
				if (!obj.optional.isPresent()) {
					return 1;
				}
				return optional.get().compareTo(obj.optional.get());
			}
			return 0;
		}
		
		@Override
		public boolean equals(Object obj) {
			boolean ret = equalsIgnoreOptional(obj);
			if (!ret) return false;
			Version that = (Version) obj;
			return this.optional.equals(that.optional);
		}
		
		/**
		* 判断两个版本是否相等，忽略 optional 信息。
		*
		* @param obj 要比较的对象
		* @return 如果版本相等（忽略 optional）则返回 true
		*/
		public boolean equalsIgnoreOptional(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Version)) return false;
			Version that = (Version) obj;
			return this.version.equals(that.version)
			&& this.pre.equals(that.pre)
			&& this.build.equals(that.build);
		}
		
		@Override
		public int hashCode() {
			int h = 1;
			int p = 17;
			
			h = p * h + version.hashCode();
			h = p * h + pre.hashCode();
			h = p * h + build.hashCode();
			h = p * h + optional.hashCode();
			
			return h;
		}
		
		/**
		* 返回标准格式的版本字符串。
		* <p>
		* 格式与 Java 21 Runtime.Version.toString() 一致。
		*
		* @return 版本字符串表示
		*/
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < version.size(); i++) {
				if (i > 0) sb.append('.');
				sb.append(version.get(i));
			}
			
			if (pre.isPresent()) {
				sb.append('-').append(pre.get());
			}
			
			if (build.isPresent()) {
				sb.append('+').append(build.get());
				if (optional.isPresent()) {
					sb.append('-').append(optional.get());
				}
				} else {
				if (optional.isPresent()) {
					sb.append(pre.isPresent() ? '-' : "+-");
					sb.append(optional.get());
				}
			}
			
			return sb.toString();
		}
		
		private static class VersionPattern {
			private static final String VNUM
			= "(?<VNUM>[1-9][0-9]*(?:(?:\\.0)*\\.[1-9][0-9]*)*)";
			private static final String PRE = "(?:-(?<PRE>[a-zA-Z0-9]+))?";
			private static final String BUILD
			= "(?:(?<PLUS>\\+)(?<BUILD>0|[1-9][0-9]*)?)?";
			private static final String OPT = "(?:-(?<OPT>[-a-zA-Z0-9.]+))?";
			private static final String VSTR_FORMAT = VNUM + PRE + BUILD + OPT;
			
			static final Pattern VSTR_PATTERN = Pattern.compile(VSTR_FORMAT);
			
			static final String VNUM_GROUP  = "VNUM";
			static final String PRE_GROUP   = "PRE";
			static final String PLUS_GROUP  = "PLUS";
			static final String BUILD_GROUP = "BUILD";
			static final String OPT_GROUP   = "OPT";
		}
	}
}