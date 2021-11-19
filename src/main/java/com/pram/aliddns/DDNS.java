package com.pram.aliddns;

import com.alibaba.fastjson.JSON;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.alidns.model.v20150109.DescribeSubDomainRecordsRequest;
import com.aliyuncs.alidns.model.v20150109.DescribeSubDomainRecordsResponse;
import com.aliyuncs.alidns.model.v20150109.UpdateDomainRecordRequest;
import com.aliyuncs.alidns.model.v20150109.UpdateDomainRecordResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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

    /**
     * 初始化
     */
    private void init(String filename) {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println("当前时间：" + dateFormat.format(date));


        String cfgStr = readJsonFile(filename);
        cfg = JSON.parseObject(cfgStr, Config.class);
        if (cfg == null) {
            System.out.println("读取配置文件" + filename + "失败");
            return;
        }
        cfg.dnsInterval *= 1000;
        cfg.ipInterval *= 1000;

        host = cfg.rr + "." + cfg.tld;
        if (cfg.rr == null || cfg.rr.length() == 0 || "@".equals(cfg.rr)) {
            // 顶级域名处理
            cfg.rr = "@";
            host = cfg.tld;
        }
        System.out.println("本机ip：" + getCurrenHostIp() + "\n主机名 " + host + " 解析中...");
    }

    /**
     * 获取当前主机公网IP
     */
    private String getCurrenHostIp() {
        // 这里使用jsonip.com第三方接口获取本地IP
        String jsonip = "https://jsonip.com";
        // 接口返回结果
        StringBuilder result = new StringBuilder();
        BufferedReader in = null;
        try {
            // 使用HttpURLConnection网络请求第三方接口
            URL url = new URL(jsonip);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();
            in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                result.append(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
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
        //  正则表达式，提取xxx.xxx.xxx.xxx，将IP地址从接口返回结果中提取出来
        String rexp = "(\\d{1,3}\\.){3}\\d{1,3}";
        Pattern pat = Pattern.compile(rexp);
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

    /**
     * 修改解析记录
     */
    private UpdateDomainRecordResponse updateDomainRecord(UpdateDomainRecordRequest request, IAcsClient client) {
        try {
            //  调用SDK发送请求
            return client.getAcsResponse(request);
        } catch (ClientException e) {
            e.printStackTrace();
            //  发生调用错误，抛出运行时异常
            throw new RuntimeException();
        }
    }

    /**
     * 检测IP是否改变，改变了就修改
     */
    private void check(String ip) {
        //  设置鉴权参数，初始化客户端
        DefaultProfile profile;
        profile = DefaultProfile.getProfile(cfg.regionId, cfg.accessKeyId, cfg.secret);
        IAcsClient client = new DefaultAcsClient(profile);

        //查询指定域名的最新解析记录
        DescribeSubDomainRecordsRequest describeSubDomainRecordsRequest = new DescribeSubDomainRecordsRequest();
        describeSubDomainRecordsRequest.setSubDomain(host);
        DescribeSubDomainRecordsResponse describeSubDomainRecordsResponse = describeSubDomainRecords(describeSubDomainRecordsRequest, client);
        List<DescribeSubDomainRecordsResponse.Record> domainRecords = describeSubDomainRecordsResponse.getDomainRecords();
        //最新的一条解析记录
        if (domainRecords.size() != 0) {
            DescribeSubDomainRecordsResponse.Record record = domainRecords.get(0);
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
                updateDomainRecordRequest.setType("A");
                UpdateDomainRecordResponse updateDomainRecordResponse = updateDomainRecord(updateDomainRecordRequest, client);

                System.out.println("主机名 " + host + " 解析地址已修改为:" + ip);

            } else {
                System.out.println("主机名 " + host + " 解析记录与当前主机公网ip " + ip + " 相同，无需修改解析地址");
            }
        } else {
            System.out.println("没有找到主机名 " + host + " 的解析记录");
        }
    }

    /**
     * 运行检测
     */
    private void run() {
        //  当前主机公网IP
        String ip = getCurrenHostIp();

        while (true) {
            try {
                check(ip);
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

            Reader reader = new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8);
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

    public static void main(String[] args) {
        String filename = (args.length == 0) ? "./config.json" : args[0];
        DDNS ddns = new DDNS();
        ddns.init(filename);
        ddns.run();
    }
}
