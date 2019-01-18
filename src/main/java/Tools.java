import com.alibaba.fastjson.JSONObject;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Tools {

    public static String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36";

    // 读, 写, 追加文件, 文本模式
    public static String readFileText(String filePath){
        // List<String> lineList = new ArrayList<String>();
        StringBuilder content = new StringBuilder("");
        try {
            String encoding = "UTF-8";
            File file = new File(filePath);
            // 判断文件是否存在
            if (file.isFile() && file.exists()) {
                InputStreamReader read = new InputStreamReader(new FileInputStream(file), encoding);
                BufferedReader bufferedReader = new BufferedReader(read);
                String lineTxt = null;
                while ((lineTxt = bufferedReader.readLine()) != null) {
                    // list.add(lineTxt);
                    content.append(lineTxt+"\n");
                }
                bufferedReader.close();
                read.close();
            }
            else {
                System.out.println("找不到指定的文件");
            }
        }
        catch (Exception e) {
            System.out.println("读取文件内容出错");
            e.printStackTrace();
        }
        return content.toString().substring(0, content.toString().length()-1);
    }
    public static boolean appendFileText(String filePath, String text){

        FileWriter fileWriter = null;

        try {
            //如果文件存在，则追加内容；如果文件不存在，则创建文件
            File file = new File(filePath);
            fileWriter = new FileWriter(file, true);
        }
        catch (IOException e) {
            return false;
        }
        PrintWriter PrintWriter = new PrintWriter(fileWriter);
        PrintWriter.println(text);
        PrintWriter.flush();

        try {
            PrintWriter.flush();
            PrintWriter.close();
        }
        catch (Exception e) {
            return false;
        }
        return true;
    }
    public static boolean writeFileText(String filePath, String text) {
        try {
            //使用这个构造函数时，如果存在kuka.txt文件，
            //则先把这个文件给删除掉，然后创建新的kuka.txt
            // FileWriter writer = new FileWriter(filePath);
            // writer.write(text);
            // writer.close();
            File file = new File(filePath);
            file.delete();
            file.createNewFile();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            writer.write(text);
            writer.close();
        }
        catch (IOException e) {
            return false;
        }
        return true;
    }

    // 获取返回结果字符串
    public static String responseToString(CloseableHttpResponse response) throws Exception{
        return EntityUtils.toString(response.getEntity(), "UTF-8");
    }

    // 创建 post 表单实体
    public static UrlEncodedFormEntity doPostData(Map<String, String> postDataMap) throws Exception{
        List<NameValuePair> postData = new LinkedList<>();
        for (Map.Entry<String, String> entry : postDataMap.entrySet()){
            BasicNameValuePair param = new BasicNameValuePair(entry.getKey(), entry.getValue());
            postData.add(param);
        }
        return new UrlEncodedFormEntity(postData, "UTF-8");
    }
    // 创建 post 表单实体 从 Json
    public static StringEntity doPostDataFromJson(Map<String, String> postDataMap) throws Exception{
        JSONObject jsonData = new JSONObject();
        for(Map.Entry<String, String> element: postDataMap.entrySet()){
            jsonData.put(element.getKey(), element.getValue());
        }
        StringEntity stringEntity = new StringEntity(jsonData.toJSONString(), "UTF-8");
        stringEntity.setContentEncoding("UTF-8");
        return stringEntity;
    }
    // URL编码
    public static String encodeURL(String url) throws Exception{
        return URLEncoder.encode(url, "UTF-8");
    }
    // URL解码
    public static String decodeURL(String url) throws Exception{
        return URLDecoder.decode(url, "UTF-8");
    }

    // 设置请求头
    public static HttpPost setRequestHeader(HttpPost httpPost, boolean hasHost, boolean isJson, boolean hasXRequest){
        httpPost.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36");
        if (hasHost){
            httpPost.addHeader("Host", "kyfw.12306.cn");
        }
        if (isJson){
            httpPost.addHeader("Content-Type", "application/json;charset=UTF-8");
        }else {
            httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        }
        if (hasXRequest){
            httpPost.addHeader("X-Requested-With", "XMLHttpRequest");
        }
        return httpPost;
    }
    public static HttpGet  setRequestHeader(HttpGet httpGet, boolean hasHost, boolean isJson, boolean hasXRequest){
        httpGet.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36");
        if (hasHost){
            httpGet.addHeader("Host", "kyfw.12306.cn");
        }
        if (isJson){
            httpGet.addHeader("Content-Type", "application/json;charset=UTF-8");
        }else {
            httpGet.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        }
        if (hasXRequest){
            httpGet.addHeader("X-Requested-With", "XMLHttpRequest");
        }
        return httpGet;
    }

    // 读取返回Json状态(这里仅仅判断连接请求是否成功)
    public static boolean getResultJsonStatus(String resultStr){
        JSONObject jsonObject = JSONObject.parseObject(resultStr);
        return jsonObject.getBoolean("status");
    }

    // 创建一个会话实例
    public static CloseableHttpClient getSession(Integer timeOut){
        // 创建http请求配置参数
        RequestConfig requestConfig = RequestConfig.custom()
                // 获取连接超时时间
                .setConnectTimeout(timeOut)
                // 请求超时时间
                .setConnectionRequestTimeout(timeOut)
                // 响应超时时间
                .setSocketTimeout(timeOut)
                // 构建对象
                .build();
        return HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
    }
}
