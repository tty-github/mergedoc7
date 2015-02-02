/*
 * Copyright (c) 2003- Shinji Kashihara. All rights reserved.
 * This program are made available under the terms of the Common Public License
 * v1.0 which accompanies this distribution, and is available at cpl-v10.html.
 */
package mergedoc.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Javadoc API ドキュメントです。
 * @author Shinji Kashihara
 */
public class APIDocument {

    /** ロガー */
    private static final Log log = LogFactory.getLog(APIDocument.class);

    /** シグネチャをキーとしたコメントのテーブル */
    private final Map<Signature, Comment> contextTable = new HashMap<Signature, Comment>();

    /**
     * see タグや link タグの埋め込みリンクパターン。
     * <pre>
     *   group(1): URL
     *   group(2): ラベル
     * </pre>
     */
    private static final Pattern linkClassPattern = PatternCache
            .getPattern("(?si)<A\\s+HREF=\"(?!ftp)(?!.*package-summary)(?!.*serialized-form)([^\"]+)\"[^>]*><CODE>(.+?)</CODE></A>");

    /**
     * コンストラクタです。
     * @param docDir API ドキュメントディレクトリ
     * @param className クラス名
     * @param charsetName 文字セット名
     * @throws IOException 入出力例外が発生した場合
     */
    public APIDocument(File docDir, String className, String charsetName) throws IOException {

        // API ドキュメント絶対パスを生成
        StringBuilder path = new StringBuilder();
        path.append(docDir.getPath());
        path.append(File.separator);
        path.append(className.replace('.', File.separatorChar));
        path.append(".html");

        // API ドキュメントファイルのロード
        File docFile = new CachedFile(path.toString());
        load(docDir, docFile, charsetName);

        // インナークラス API ドキュメントファイルのロード
        // prefix は毎回異なるため PatternCache は使用しない
        String prefix = FastStringUtils.replaceFirst(docFile.getName(), "\\.html$", "");
        Pattern innerClass = Pattern.compile(prefix + "\\..+\\.html$");

        for (File f : docFile.listFiles()) {
            if (innerClass.matcher(f.getName()).matches()) {
                load(docDir, f, charsetName);
            }
        }
    }

    /**
     * ファイルクラスのプロキシです。<br>
     * リストキャッシュ機能を持ちます。
     */
    private static class CachedFile extends File {

        private static File cachedDir;
        private static File[] cachedFiles;
        private static final File[] EMPTY_FILES = new File[0];

        public CachedFile(String path) {
            super(path);
        }

        @Override
        public File[] listFiles() {
            File dir = getParentFile();
            if (!dir.equals(cachedDir)) {
                cachedDir = dir;
                cachedFiles = dir.listFiles();
                if (cachedFiles == null) {
                    cachedFiles = EMPTY_FILES;
                }
            }
            return cachedFiles;
        }
    }

    /**
     * API ドキュメント HTML ファイルを読み込みます。
     * @param docDir API ドキュメントディレクトリ
     * @param docFile API ドキュメントファイル
     * @param charsetName 文字セット名
     * @throws IOException 入出力例外が発生した場合
     */
    private void load(File docDir, File docFile, String charsetName) throws IOException {

        // 存在しない場合は何もしない
        if (!docFile.exists()) {
            return;
        }

        // API ドキュメント読み込み
        InputStream is = new FileInputStream(docFile);
        byte[] buf = new byte[is.available()];
        is.read(buf);
        is.close();
        String docHtml = new String(buf, charsetName);
        docHtml = FastStringUtils.optimizeLineSeparator(docHtml);
        docHtml = docHtml.replace('\t', ' ');

        // WAVE DASH 文字化け回避
        char wavaDash = (char) Integer.decode("0x301c").intValue();
        docHtml = docHtml.replace(wavaDash, '～');

        // API ドキュメントファイルパスからクラス名取得
        String className = FastStringUtils.replaceFirst(docFile.getPath(), "\\.html$", "");
        className = className.replace(docDir.getPath() + File.separator, ""); //Patternキャッシュしない
        className = className.replace(File.separatorChar, '.');

        // StringBuffer、StringBuilder だけの特殊処理
        if (className.equals("java.lang.StringBuffer") || className.equals("java.lang.StringBuilder")) {
            docHtml = docHtml.replace("%20", "");
        }

        // API ドキュメントのコメント解析
        Document doc = Jsoup.parse(docHtml);
        doc.outputSettings().prettyPrint(false);
        parseClassComment(className, doc);
        parseMethodComment(className, doc);
    }

    /**
     * コンテキストが空か判定します。
     * @return コンテキストが空の場合は true
     */
    public boolean isEmpty() {
        return contextTable.isEmpty();
    }

    /**
     * 指定したシグネチャを持つ Javadoc コメントを取得します。
     * @param signature シグネチャ
     * @return Javadoc コメント
     */
    public Comment getComment(Signature signature) {
        Comment comment = contextTable.get(signature);
        return comment;
    }

    /**
     * クラスの Javadoc コメント情報を作成します。
     * author, version タグは Javadoc デフォルトでは存在しないため解析しません。<br>
     * @param className クラス名
     * @param docHtml API ドキュメントソース
     */
    private void parseClassComment(String className, Document doc) {
        Elements elements = doc.select("body > div.contentContainer > div.description > ul > li");
        for (Element element : elements) {
            String sigStr = element.select("pre").first().html();
            Signature sig = createSignature(className, sigStr);
            Comment comment = new Comment(sig);

            // deprecated タグ
            String depre = "";
            Elements divs = element.select("div");
            if (divs.size() == 2) {
                depre = divs.get(0).html();
            }
            parseDeprecatedTag(className, depre, comment);

            // コメント本文
            if (divs.size() > 0) {
                String body = divs.last().html();
                body = formatLinkTag(className, body);
                comment.setDocumentBody(body);
            }

            // 共通タグ
            parseCommonTag(className, element, comment);

            log.debug(sig);
            contextTable.put(sig, comment);
        }
    }

    /**
     * メソッドやフィールドの Javadoc コメント情報を作成します。
     * @param className クラス名
     * @param docHtml API ドキュメントソース
     */
    private void parseMethodComment(String className, Document doc) {
        Elements elements = doc.select("body > div.contentContainer > div.details > ul > li > ul > li > ul > li");
        for (Element element : elements) {
            Element sigElm = element.select("pre").first();
            if (sigElm == null) {
                continue;
            }
            String sigStr = sigElm.html();
            Signature sig = createSignature(className, sigStr);
            Comment comment = new Comment(sig);

            // deprecated タグ
            String depre = "";
            Elements divs = element.select("div");
            if (divs.size() == 2) {
                depre = divs.get(0).html();
            }
            if (divs.size() > 0) {
                String body = divs.last().html();
                body = formatLinkTag(className, body);
                comment.setDocumentBody(body);
            }

            Elements dtTags = element.select("dl dt");
            for (Element dtTag : dtTags) {
                String dtText = dtTag.text();
                if (dtText.contains("パラメータ:")) {
                    Element dd = dtTag;
                    while (true) {
                        dd = dd.nextElementSibling();
                        if (dd == null || dd.tagName().equalsIgnoreCase("dd") == false) {
                            break;
                        }
                        String name = dd.select("code").first().text();
                        if (dtText.contains("型パラメータ:")) {
                            name = "<" + name + ">";
                        }
                        String items = dd.html();
                        Pattern p = PatternCache.getPattern("(?si)<CODE>(.+?)</CODE>\\s*-\\s*(.*?)(<DD>|</DD>|</DL>|<DT>|$)");
                        Matcher m = p.matcher(items);
                        if (m.find()) {
                            String desc = formatLinkTag(className, m.group(2));
                            comment.addParam(name, desc);
                        }
                    }
                    continue;
                }

                if (dtText.contains("戻り値:")) {
                    Element dd = dtTag.nextElementSibling();
                    String str = dd.html();
                    str = formatLinkTag(className, str);
                    comment.addReturn(str);
                    continue;
                }

                if (dtText.contains("例外:")) {
                    Element dd = dtTag;
                    while (true) {
                        dd = dd.nextElementSibling();
                        if (dd == null || dd.tagName().equalsIgnoreCase("dd") == false) {
                            break;
                        }
                        String name = dd.select("code").first().text();
                        String items = dd.html();
                        Pattern p = PatternCache.getPattern("(?si)<CODE>(.+?)</CODE>\\s*-\\s*(.*?)(<DD>|</DD>|</DL>|<DT>|$)");
                        Matcher m = p.matcher(items);
                        if (m.find()) {
                            String desc = formatLinkTag(className, m.group(2));
                            String param = name + " " + desc;
                            comment.addThrows(param);
                        }
                    }
                    continue;
                }

            }
            // deprecated タグ
            parseDeprecatedTag(className, depre, comment);

            // 共通タグ
            parseCommonTag(className, element, comment);

            contextTable.put(sig, comment);
        }
    }

    /**
     * シグネチャを作成します。
     * @param className クラス名
     * @param sig シグネチャ文字列
     */
    private Signature createSignature(String className, String sig) {
        sig = FastStringUtils.replaceAll(sig, "(?s)<.+?>", " "); // タグ除去
        sig = FastStringUtils.replaceAll(sig, "\\&nbsp;", " "); // 空白置換
        sig = FastStringUtils.replaceAll(sig, "\\&lt;", "<"); // 型引数開始タグ
        sig = FastStringUtils.replaceAll(sig, "\\&gt;", ">"); // 型引数終了タグ
        sig = FastStringUtils.replaceFirst(sig, "(?s)\\sthrows\\s.*", "");
        Signature signature = new Signature(className, sig);
        return signature;
    }

    /**
     * Javadoc の 共通タグを解析しコメントに追加します。
     * @param className クラス名
     * @param context 評価コンテキスト
     * @param comment コメント
     */
    private void parseCommonTag(String className, Element element, Comment comment) {
        Elements dts = element.select("dl dt");
        for (Element dt : dts) {
            String dtText = dt.text();
            if (dtText.contains("関連項目")) {
                Elements aTags = dt.nextElementSibling().select("a:has(code)");
                for (Element a : aTags) {
                    String url = a.attr("href");
                    String ref;
                    if (a.childNodeSize() != 1) {
                        ref = aTags.outerHtml();
                    } else {
                        ref = formatClassName(className, url);
                        ref = FastStringUtils.replace(ref, "%28", "(");
                        ref = FastStringUtils.replace(ref, "%29", ")");

                        Pattern methodRefPat = PatternCache.getPattern("-(.*)-$");
                        Matcher methodRefMat = methodRefPat.matcher(ref);
                        if (methodRefMat.find()) {
                            ref = FastStringUtils.replaceAll(ref, "-(.*)-$", "($1)"); // for Java8
                            ref = FastStringUtils.replace(ref, "-", ","); // for Java8
                            ref = FastStringUtils.replace(ref, ":A", "[]"); // for Java8
                        }
                    }
                    comment.addSee(ref);
                }
            } else if (dtText.contains("導入されたバージョン:")) {
                comment.addSince(dt.nextElementSibling().text());
            }
        }
    }

    /**
     * Javadoc の deprecated タグを解析しコメントに追加します。
     * @param context 評価コンテキスト
     * @param comment コメント
     */
    private void parseDeprecatedTag(String className, String context, Comment comment) {
        if (context.contains("非推奨")) {
            Pattern pat = PatternCache.getPattern("(?si)<span class=\"strong\">非推奨。.+?<I>(.+?)</I>");
            Matcher mat = pat.matcher(context);
            if (mat.find()) {
                String str = mat.group(1);
                str = formatLinkTag(className, str);
                comment.addDeprecated(str);
            }
        }
    }

    /**
     * HTML の A タグを Javadoc の link タグにフォーマットします。
     * <p>
     * 責務的には Javadoc タグの整形は Comment クラスで行うべきですが、
     * 今のところ、Javadoc の see タグとのからみもあり、このクラスで
     * 処理しています。
     *
     * @param className クラス名
     * @param html HTML の A タグを含む文字列
     * @return Javadoc link タグ文字列
     */
    private String formatLinkTag(String className, String html) {

        StringBuffer sb = new StringBuffer();
        Matcher linkMatcher = linkClassPattern.matcher(html);

        while (linkMatcher.find()) {

            String url = linkMatcher.group(1).trim();
            String label = linkMatcher.group(2).trim();
            String ref = formatClassName(className, url);
            Pattern methodRefPat = PatternCache.getPattern("-(.*)-$");
            Matcher methodRefMat = methodRefPat.matcher(ref);
            if (methodRefMat.find()) {
                ref = FastStringUtils.replaceAll(ref, "-(.*)-$", "($1)"); // for Java8
                ref = FastStringUtils.replace(ref, "-", ","); // for Java8
                ref = FastStringUtils.replace(ref, ":A", "[]"); // for Java8
            }

            StringBuilder link = new StringBuilder();
            link.append("{@link ");
            link.append(ref);
            if (label.length() > 0) {

                ref = ref.replace('#', '.');
                if (!ref.endsWith(label)) {
                    link.append(" ");
                    link.append(label);
                }
            }
            link.append("}");

            linkMatcher.appendReplacement(sb, link.toString());
        }
        linkMatcher.appendTail(sb);
        html = sb.toString();

        return html;
    }

    /**
     * see タグや link タグの URL を package.class#member 形式にフォーマットします。
     * 同一パッケージの場合は package が省略され、同一クラスの場合は class も省略されます。
     * @param className クラス名
     * @param path パス
     * @return package.class#member 形式の文字列
     */
    private String formatClassName(String className, String path) {

        String lastClassName = FastStringUtils.replaceFirst(className, ".+\\.", "");
        String packageName = className.replace("." + lastClassName, ""); // Patternキャッシュしない
        String lastClassPrefix = "\\.([A-Z])";

        path = FastStringUtils.replace(path, ".html", "");
        path = path.replace('/', '.');
        path = FastStringUtils.replaceFirst(path, "^\\.*", "");
        path = FastStringUtils.replaceAll(path, "java.lang" + lastClassPrefix, "$1");
        path = path.replaceAll(packageName + lastClassPrefix, "$1"); // Patternキャッシュしない
        path = path.replaceAll("^" + lastClassName + "#", "#"); // Patternキャッシュしない
        path = path.replaceAll(".package-summary$", "");
        return path;
    }
}
