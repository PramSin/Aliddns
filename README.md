# AliDDNS 2.1.0

阿里云动态DDNS配置的 maven 项目，Java
版本为16.0.1。运行程序后，程序会自动获取当前主机公网ip地址（支持ipv6）与配置文件中主机名的解析记录，若解析记录与当前公网ip不匹配，则更改域名解析，否则不做更改；若无法查询到ip地址，则删除相关解析设置。ip地址的获取由 [jsonip.com](https://jsonip.com)
网站提供技术支持。

本程序改编自 https://github.com/zngw/aliddns

## 配置

文件 config.json 如下所示

```json
{
   "regionId": "cn-shanghai",
   "accessKeyId": "AccessKeyID",
   "secret": "AccessKey Secret",
   "tld": "xxx.xxx",
   "rr": "xxx"
}
```

* regionId: 区域,域名管理一般是上海"cn-shanghai"
* accessKeyId: 对相应域名解析具有增删改查权限的账号的AccessKeyID
* secret: 对相应域名解析具有增删改查权限的账号的AccessKey Secret
* tld: 顶级域名
* rr: 主机名

## 启动

1. 使用`mvn package`将 DDNS.java 打包为 jar 可执行文件。
2. 在文件 config.json 的路径下 `java -jar /your/path/for/jar/file/aliddns-2.0.0.jar` 或指定 config.json
   文件的路径 `java -jar /your/path/for/jar/file/aliddns-2.0.0.jar /your/path/for/json/file/config.json`

可以使用`crontab`来定时进行动态域名解析。

## 注意

请不要更改 pom.xml 文件中 `aliyun-java-sdk-core` 的版本，否则会由于它使用的 `gson` 版本与 `aliyun-java-sdk-alidns` 不同而报错。
