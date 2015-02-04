Java7用MergeDoc
===============
[MergeDoc](http://mergedoc.sourceforge.jp/#mergedoc.html)のJava7対応版

Eclipse は日本語化しても Java 標準 API のメソッドなどにマウスを合わせたときにホバー表示される説明は日本語化されません。
これは Eclipse が JDK 付属の Java ソースから Javadoc コメントを取得し表示しているためです。MergeDoc は日本語のホバー表示を可能にするために Java ソースと[Java SE　日本語ドキュメントアーカイブ](http://www.oracle.com/technetwork/jp/java/java-sun-1440465-ja.html)をマージするツールです。

またホバー表示以外にも次のような場合に参照するソースもコメントが日本語になります。

- Java 標準 API のメソッドを Ctrl + 左クリック
- Java 標準 API の例外スタックトレースからジャンプ
- パッケージエクスプローラから rt.jar を辿ってクラスファイルを開く

Java7用になった以外は基本的にオリジナルと同じです。オリジナルの説明は[readme.txt](readme.txt)をご覧ください。

## 使用方法

実行環境: Java 7 以上

### インストール

[mergedoc7-master.zip](https://github.com/tty-github/mergedoc7/archive/master.zip)を適当な場所に解凍してください。

以前のバージョンがある場合は置換定義ファイルを上書きしてしまうのでカスタマイズ している場合は事前に退避してください。

### アンインストール

ディレクトリごと削除してください。

### 起動方法

Windows で JAR ファイルに JRE/JDK7 以上の javaw を関連付けている場合は mergedoc.jar ダブルクリックで起動します。

それ以外の場合は次のようなコマンドで起動してください。

```
java -jar mergedoc.jar
```

-server オプションも合わせて指定することで、高速化される場合があります。


### 操作方法

起動後の設定画面で設定を行い実行ボタンを押下してください。
ファイルやディレクトリはドラッグ＆ドロップでも指定可能です。


#### 基本設定

##### API ドキュメントディレクトリ

日本語 API ドキュメントを格納しているディレクトリを指定。
package-list ファイルがあるディレクトリです。

例）
```
C:\E40751_01\api
```
エンコーディングはUTF-8を指定してください。

##### 入力ソースアーカイブファイル

Java ソースアーカイブファイルを指定。
ファイル形式は「.zip」「.jar」「.tar.gz」「.tgz」のいずれか。

例）
```
C:\jdk1.7.0\src.zip
```

エンコーディングは JDK の場合、ASCII 以外の文字が含まれていないので何でもかまいません。


##### 出力ソースアーカイブファイル

ソースを出力する新しいアーカイブファイルを指定。ファイル形式は「.zip」「.jar」のいずれか。

例）
```
C:\jdk1.7.0\srcja_utf8.zip
```

エンコーディングは使用するエディタや IDE の設定に合わせてください。
ただし、UTF-8以外を指定すると、「&trade;」等の一部の文字が文字化けします。


## その他

* Java6以前には対応していません。
* 詳細設定にチェックをつけた場合の動作確認はしていません。
* 頻繁に使用するものではないので、パフォーマンスチューニングはほとんどしていません。

## ライセンス

[Common Public License v1.0][CPL]

## 使用しているソフトウェアのライセンス

* jsoup - [MIT License][mit].
* jsoup以外 - [Apache License, Version 2.0][ASL]

[CPL]: http://opensource.org/licenses/cpl1.0.php
[MIT]: http://opensource.org/licenses/mit-license.php
[ASL]: http://www.apache.org/licenses/LICENSE-2.0

