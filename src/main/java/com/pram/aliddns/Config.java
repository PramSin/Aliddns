package com.pram.aliddns;

/**
 * ItemLogReason class
 *
 * @author 55
 * @date 2021/6/28
 */
public class Config {
    public String regionId;         // 地域ID,"cn-hangzhou"
    public String accessKeyId;      // 您的AccessKey ID
    public String secret;
    public String tld;              // 顶级域名
    public String rr;               // 子域名
    public String maxRuns;          // 最大运行次数（无法成功运行时防止程序卡死）
    public Boolean ipv4;            // 是否对ipv4地址进行动态解析
    public Boolean ipv6;            // 是否对ipv6地址进行动态解析
}
