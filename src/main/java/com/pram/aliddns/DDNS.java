package com.pram.aliddns;

import com.alibaba.fastjson.JSON;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.alidns.model.v20150109.*;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DDNS class
 *
 * @author 55
 * @date 2021/6/28
 */
public class DDNS {
    private String host = null;         // 完整域名
    private Config cfg = null;          // 配置文件

    public static void main(String[] args) {
        String filename = (args.length == 0) ? "./config.json" : args[0];
        DDNS ddns = new DDNS();
        String[] ip = ddns.init(filename);
        String ipv4 = ip[0];
        String ipv6 = ip[1];
        ddns.run(ipv4, ipv6);
    }

    /**
     * 初始化
     */
    private String[] init(String filename) {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println("\n----- 当前时间：" + dateFormat.format(date) + " -----\n");

        String cfgStr = readJsonFile(filename);
        cfg = JSON.parseObject(cfgStr, Config.class);
        if (cfg == null) {
            System.out.println("读取配置文件" + filename + "失败");
            return new String[]{"", ""};
        }
        cfg.dnsInterval *= 1000;
        cfg.ipInterval *= 1000;

        host = cfg.rr + "." + cfg.tld;
        if (cfg.rr == null || cfg.rr.length() == 0 || "@".equals(cfg.rr)) {
            // 顶级域名处理
            cfg.rr = "@";
            host = cfg.tld;
        }
        return new String[]{getCurrenHostIpv4(), getCurrenHostIpv6()};
    }

    private StringBuilder getJsonResponse(String json_ip) {
        // 接口返回结果
        StringBuilder result = new StringBuilder();
        BufferedReader in = null;
        try {
            // 使用HttpURLConnection网络请求第三方接口
            URL url = new URL(json_ip);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();
            in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                result.append(line);
            }
        } catch (Exception e) {
            return new StringBuilder();
        }
        // 使用finally块来关闭输入流
        finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
        return result;
    }

    /**
     * 获取当前主机公网IP
     */
    private String getCurrenHostIpv4() {
        // 这里使用jsonip.com第三方接口获取本地IP
        String jsonip = "https://ipv4.jsonip.com/";

        StringBuilder result = getJsonResponse(jsonip);

        //  正则表达式，提取xxx.xxx.xxx.xxx，将IP地址从接口返回结果中提取出来
        String rex = "(\\d{1,3}\\.){3}\\d{1,3}";
        Pattern pat = Pattern.compile(rex);
        Matcher mat = pat.matcher(result.toString());
        String res = "";
        if (mat.find()) {
            res = mat.group();
        }
        return res;
    }

    /**
     * 获取主域名的所有解析记录列表
     */
    private DescribeSubDomainRecordsResponse describeSubDomainRecords(DescribeSubDomainRecordsRequest request, IAcsClient client) {
        try {
            // 调用SDK发送请求
            return client.getAcsResponse(request);
        } catch (ClientException e) {
            e.printStackTrace();
            // 发生调用错误，抛出运行时异常
            throw new RuntimeException();
        }
    }

    private String getCurrenHostIpv6() {
        // 这里使用jsonip.com第三方接口获取本地IP
        String jsonip = "https://ipv6.jsonip.com/";

        StringBuilder result = getJsonResponse(jsonip);

        //  正则表达式，提取xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx，将IP地址从接口返回结果中提取出来
        String rex = "([\\da-fA-F]{1,4}:){7}[\\da-fA-F]{1,4}";
        Pattern pat = Pattern.compile(rex);
        Matcher mat = pat.matcher(result.toString());
        String res = "";
        if (mat.find()) {
            res = mat.group();
        }
        return res;
    }

    /**
     * 添加DNS解析记录
     */
    private void addDNSRecord(IAcsClient client, String ip, Boolean is_v6) {
        //  修改解析记录
        AddDomainRecordRequest addDomainRecordRequest = new AddDomainRecordRequest();
        //  域名名称
        addDomainRecordRequest.setDomainName(host);
        //  主机记录
        addDomainRecordRequest.setRR(cfg.rr);
        //  将主机记录值改为当前主机IP
        addDomainRecordRequest.setValue(ip);
        //  解析记录类型
        if (is_v6) {
            addDomainRecordRequest.setType("AAAA");
            System.out.println("\n----添加ipv6解析地址----");
        } else {
            addDomainRecordRequest.setType("A");
            System.out.println("\n----添加ipv4解析地址----");
        }

        try {
            //  调用SDK发送请求
            client.getAcsResponse(addDomainRecordRequest);
        } catch (ClientException e) {
            e.printStackTrace();
            //  发生调用错误，抛出运行时异常
            throw new RuntimeException();
        }

        System.out.println("主机名 " + host + " 解析地址添加:" + ip + "\n");
    }

    /**
     * 修改DNS解析记录
     */
    private void updateDNSRecord(DescribeSubDomainRecordsResponse.Record record, IAcsClient client, String ip, Boolean is_v6) {
        //  记录ID
        String recordId = record.getRecordId();
        //  记录值
        // 域名IP
        String recordsIp = record.getValue();

        if (!ip.equals(recordsIp)) {
            //  修改解析记录
            UpdateDomainRecordRequest updateDomainRecordRequest = new UpdateDomainRecordRequest();
            //  主机记录
            updateDomainRecordRequest.setRR(cfg.rr);
            //  记录ID
            updateDomainRecordRequest.setRecordId(recordId);
            //  将主机记录值改为当前主机IP
            updateDomainRecordRequest.setValue(ip);
            //  解析记录类型
            if (is_v6) {
                updateDomainRecordRequest.setType("AAAA");
                System.out.println("\n----更新ipv6解析地址----");
            } else {
                updateDomainRecordRequest.setType("A");
                System.out.println("\n----更新ipv4解析地址----");
            }

            try {
                //  调用SDK发送请求
                client.getAcsResponse(updateDomainRecordRequest);
            } catch (ClientException e) {
                e.printStackTrace();
                //  发生调用错误，抛出运行时异常
                throw new RuntimeException();
            }

            System.out.println("主机名 " + host + " 解析地址已修改为:" + ip + "\n");

        } else {
            System.out.println("主机名 " + host + " 解析记录与当前主机公网ip " + ip + " 相同，无需修改解析地址\n");
        }
    }

    private void deleteDNSRecord(DescribeSubDomainRecordsResponse.Record record, IAcsClient client, Boolean is_v6) {
        //  修改解析记录
        DeleteDomainRecordRequest deleteDomainRecordRequest = new DeleteDomainRecordRequest();
        //  记录ID
        deleteDomainRecordRequest.setRecordId(record.getRecordId());
        if (is_v6) {
            System.out.println("\n----删除ipv6解析地址----");
        } else {
            System.out.println("\n----删除ipv4解析地址----");
        }

        try {
            //  调用SDK发送请求
            client.getAcsResponse(deleteDomainRecordRequest);
        } catch (ClientException e) {
            e.printStackTrace();
            //  发生调用错误，抛出运行时异常
            throw new RuntimeException();
        }

        System.out.println("主机名 " + host + " 解析地址已删除\n");
    }

    /**
     * 检测IP状态与解析记录，并进行解析记录的增删改
     */
    private void check(String ipv4, String ipv6) {

        //  设置鉴权参数，初始化客户端
        DefaultProfile profile;
        profile = DefaultProfile.getProfile(cfg.regionId, cfg.accessKeyId, cfg.secret);
        IAcsClient client = new DefaultAcsClient(profile);

        //查询指定域名的最新解析记录
        DescribeSubDomainRecordsRequest describeSubDomainRecordsRequest = new DescribeSubDomainRecordsRequest();
        describeSubDomainRecordsRequest.setSubDomain(host);
        DescribeSubDomainRecordsResponse describeSubDomainRecordsResponse = describeSubDomainRecords(describeSubDomainRecordsRequest, client);

        List<DescribeSubDomainRecordsResponse.Record> domainRecords = describeSubDomainRecordsResponse.getDomainRecords();

        DescribeSubDomainRecordsResponse.Record v4Record = null;
        DescribeSubDomainRecordsResponse.Record v6Record = null;
        for (DescribeSubDomainRecordsResponse.Record record :
                domainRecords) {

            if (record.getType().equals("A")) {
                v4Record = record;
            } else if (record.getType().equals("AAAA")) {
                v6Record = record;
            }
        }

        if (ipv4.equals("")) {
            System.out.println("本机ipv4无法获取！\n主机名 " + host + " 解析中...");
            if (v4Record != null) {
                deleteDNSRecord(v4Record, client, false);
            }
        } else {
            System.out.println("本机ipv4：" + ipv4 + "\n主机名 " + host + " 解析中...");
            if (v4Record != null) {
                updateDNSRecord(v4Record, client, ipv4, false);
            } else {
                addDNSRecord(client, ipv4, false);
            }
        }
        if (ipv6.equals("")) {
            System.out.println("本机ipv6无法获取！\n主机名 " + host + " 解析中...");
            if (v6Record != null) {
                deleteDNSRecord(v6Record, client, true);
            }
        } else {
            System.out.println("本机ipv6：" + ipv6 + "\n主机名 " + host + " 解析中...");
            if (v6Record != null) {
                updateDNSRecord(v6Record, client, ipv6, true);
            } else {
                addDNSRecord(client, ipv6, true);
            }
        }
    }

    /**
     * 运行检测
     */
    private void run(String ipv4, String ipv6) {
        while (true) {
            try {
                check(ipv4, ipv6);
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println();
    }

    /**
     * 读取json文件，返回json串
     */
    public String readJsonFile(String fileName) {
        String jsonStr;
        try {
            File jsonFile = new File(fileName);
            FileReader fileReader = new FileReader(jsonFile);

            Reader reader = new InputStreamReader(Files.newInputStream(jsonFile.toPath()), StandardCharsets.UTF_8);
            int ch;
            StringBuilder sb = new StringBuilder();
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }

            fileReader.close();
            reader.close();
            jsonStr = sb.toString();
            return jsonStr;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
