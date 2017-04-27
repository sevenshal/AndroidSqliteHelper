sqlitehelper
============

`sqlitehelper` 是一个sqlite的服务，你可以通过telnet 命令远程控制应用在运行时的数据库内容，而不需要将数据库pull到pc操作。

## 安装

```groovy
allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}

dependencies {
    	        compile 'com.github.sevenshal:AndroidSqliteHelper:1.+'
    	}
```

## 例子

```sh
$ adb shell am startservice -n com.sevenshal.sqlitehelper/sqlitehelper.SqliteHelperService
$ adb forward tcp:33060 tcp:33060
$ telnet localhost 33060
```

## 原理

随App启动一个后台服务，通过tcp监听33060端口，PC端通过telnet连接该服务，通过telnet客户端输入指令控制数据库。

如果你知道手机的ip地址 可以直接telnet mobile_ip 33060
或者通过 adb forward tcp:33060 tcp:33060 映射到PC，可以获得更快的传输速度。
## 下一步计划

实现jdbc驱动，通过android studio的database navigator 插件可视化控制数据库。