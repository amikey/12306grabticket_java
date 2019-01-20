import org.apache.http.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.tools.Tool;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Captcha{



    // private CloseableHttpClient httpClient = HttpClients.createDefault();

    // 连接会话
    private CloseableHttpClient session;

    // 构造器
    Captcha(CloseableHttpClient session){
        this.session = session;
    }

    // 设置日志记录
    private static final Logger logger = LoggerFactory.getLogger(Captcha.class);

    /**
     *      获取验证码
     *
     * @return              GetCaptchaReturnResult 对象
     *                      status, result, timeValue, paramsCallback
     *                      布尔    字符串   字符串      字符串
     *
     * @throws Exception    异常捕获
     */
    public GetCaptchaReturnResult getCaptcha() throws Exception{
        // 获取时间戳
        String timeValue = String.valueOf(System.currentTimeMillis());
        // 获取验证码回调验证参数
        String paramsCallback = getCheckCode() + "_" + timeValue;
        // 构造请求参数
        URI uri = new URIBuilder("https://kyfw.12306.cn/passport/captcha/captcha-image64")
                .setParameter("login_site", "E")
                .setParameter("module", "login")
                .setParameter("rand", "sjrand")
                .setParameter(timeValue, "")
                .setParameter("callback", paramsCallback)
                .setParameter("_ ", timeValue)
                .build();
        // 创建Get请求
        HttpGet httpGet = Tools.setRequestHeader(new HttpGet(uri), true, false, false);
        // 创建连接结果对象
        CloseableHttpResponse response = null;
        // 创建返回结果对象
        GetCaptchaReturnResult getCaptchaReturnResult = new GetCaptchaReturnResult();
        try{
            // 执行并得到结果
            response = this.session.execute(httpGet);
            // 状态码为200则执行
            if (response.getStatusLine().getStatusCode() == 200){
                // 得到返回文本
                String responseText = Tools.responseToString(response);
                // 获取响应头, 判断返回请求格式
                boolean isJson = false;
                boolean isXml  = false;
                Header[] headers = response.getAllHeaders();
                // 遍历响应头获取连接类型
                for (Header header: headers){
                    if ("Content-Type".equals(header.getName())){
                        if ("application/json;charset=UTF-8".equals(header.getValue())){
                            isJson = true;
                            break;
                        }
                        if ("application/xhtml+xml;charset=UTF-8".equals(header.getValue())){
                            isXml = true;
                            break;
                        }
                    }
                }
                if (isJson) {
                    String captchaBase64Str = getCaptchaBase64FromJson(responseText);
                    if (!"".equals(captchaBase64Str)){
                        getCaptchaReturnResult.setStatus(true);
                        getCaptchaReturnResult.setResult(captchaBase64Str);
                        getCaptchaReturnResult.setTimeValue(timeValue);
                        getCaptchaReturnResult.setParmasCallback(paramsCallback);
                        return getCaptchaReturnResult;
                    }
                }
                if (isXml){
                    String captchaBase64Str = getCaptchaBase64FromXml(responseText);
                    if (!"".equals(captchaBase64Str)){
                        getCaptchaReturnResult.setStatus(true);
                        getCaptchaReturnResult.setResult(captchaBase64Str);
                        getCaptchaReturnResult.setTimeValue(timeValue);
                        getCaptchaReturnResult.setParmasCallback(paramsCallback);
                        return getCaptchaReturnResult;
                    }
                }
            }
        }
        finally {
            if (response != null){
                response.close();
            }
        }
        getCaptchaReturnResult.setStatus(false);
        getCaptchaReturnResult.setResult("");
        getCaptchaReturnResult.setTimeValue("");
        getCaptchaReturnResult.setParmasCallback("");
        return getCaptchaReturnResult;
    }


    /**
     *      标记验证码
     *
     *      这个方法从自建标记服务器获取结果
     *
     * @param captchaBase64Str  验证码base64字符串
     * @return                  MarkCaptchaReturnResult 对象
     *                          status, markResult
     * @throws Exception        异常处理
     */
    public MarkCaptchaReturnResult markCaptchaV1(String captchaBase64Str) throws Exception{
        CloseableHttpClient markHttpClient = HttpClients.createDefault();
        // 标记服务器地址
        String markURL = "http://192.168.1.252:4999/mark_captcha/12306/";
        // 创建Post请求对象
        HttpPost markHttpPost = Tools.setRequestHeader(new HttpPost(markURL), false, true, false);
        // 创建请求表单
        Map<String, String> markCaptchaV1Data = new HashMap<>();
        markCaptchaV1Data.put("image_base64", captchaBase64Str);
        // 设置请求body实体
        markHttpPost.setEntity(Tools.doPostData(markCaptchaV1Data));
        // 创建 response 对象
        CloseableHttpResponse response = null;
        // 创建返回结果对象
        MarkCaptchaReturnResult markCaptchaReturnResult = new MarkCaptchaReturnResult();
        try{
            response = markHttpClient.execute(markHttpPost);
            if (response.getStatusLine().getStatusCode() == 200){
                String responseText = Tools.responseToString(response);
                String markResutl = JSONObject.parseObject(responseText).getString("result");
                markResutl = markResutl.replace("[", "").replace("]", "");
                markCaptchaReturnResult.setStatus(true);
                markCaptchaReturnResult.setResult(markResutl);
                return markCaptchaReturnResult;
            }
        }
        finally {
            if (response != null){
                response.close();
            }
            markHttpClient.close();
        }
        markCaptchaReturnResult.setStatus(false);
        markCaptchaReturnResult.setResult("");
        return markCaptchaReturnResult;
    }

    /**
     * 标记验证码
     *
     *      这个方法送一个公共标记服务器获取结果
     *
     * @param captchaBase64Str      验证码base64字符串
     * @return                      MarkCaptchaReturnResult 对象
     *                              status, markResult
     * @throws Exception            异常处理
     */
    public MarkCaptchaReturnResult markCaptchaV2(String captchaBase64Str) throws Exception{
        CloseableHttpClient markHttpClient = HttpClients.createDefault();

        // 需要用到的URL
        String checkURL = "http://60.205.200.159/api";
        String markURL  = "http://check.huochepiao.360.cn/img_vcode";
        // 创建连接对象
        HttpPost httpPostcheck = Tools.setRequestHeader(new HttpPost(checkURL), false, true, false);
        // 创建请求数据
        Map<String, String> markCaptchaV2CheckData = new HashMap<>();
        markCaptchaV2CheckData.put("base64", captchaBase64Str);
        // 设置请求数据
        httpPostcheck.setEntity(Tools.doPostDataFromJson(markCaptchaV2CheckData));
        // 声明请求结果变量
        CloseableHttpResponse response = null;
        // 创建返回对象
        MarkCaptchaReturnResult markCaptchaReturnResult = new MarkCaptchaReturnResult();
        try{
            response = markHttpClient.execute(httpPostcheck);
            if (response.getStatusLine().getStatusCode() == 200){
                String responseText = Tools.responseToString(response);
                JSONObject checkJsonData = JSONObject.parseObject(responseText);
                String check = checkJsonData.getString("check");
                Boolean success = checkJsonData.getBoolean("success");
                // 如果获取到check码
                if (success){
                    HttpPost httpPostMark = Tools.setRequestHeader(new HttpPost(markURL), false, true, false);
                    // 创建请求数据
                    JSONObject postMarkJsonData = new JSONObject();
                    postMarkJsonData.put("img_buf", captchaBase64Str);
                    postMarkJsonData.put("type", "D");
                    postMarkJsonData.put("logon", 1);
                    postMarkJsonData.put("check", check);
                    postMarkJsonData.put("=", "");
                    StringEntity postMarkJsonStr = new StringEntity(postMarkJsonData.toJSONString(), "UTF-8");
                    // 设置编码格式
                    postMarkJsonStr.setContentEncoding("UTF-8");
                    // 设置请求数据
                    httpPostMark.setEntity(postMarkJsonStr);
                    // 执行请求
                    response = markHttpClient.execute(httpPostMark);
                    if (response.getStatusLine().getStatusCode() == 200){
                        responseText = Tools.responseToString(response);
                        String markResutl = JSONObject.parseObject(responseText).getString("res");
                        markResutl = markResutl.replace("(","").replace(")", "");
                        markCaptchaReturnResult.setStatus(true);
                        markCaptchaReturnResult.setResult(markResutl);
                        return markCaptchaReturnResult;
                    }
                }
            }
        }
        finally {
            if (response != null){
                response.close();
            }
            markHttpClient.close();
        }
        markCaptchaReturnResult.setStatus(false);
        markCaptchaReturnResult.setResult("");
        return markCaptchaReturnResult;
    }

    /**
     *      检查验证码
     *
     * @param paramsCallback    用于验证验证码的时间戳参数
     * @param markResult        标记结果
     * @param timeValue         时间戳
     * @return                  CheckCaptchaReturnResult 对象
     *                          status， session
     * @throws Exception        异常处理
     */
    public CheckCaptchaReturnResult checkCaptcha(String paramsCallback, String markResult, String timeValue) throws Exception{
        String checkURL = "https://kyfw.12306.cn/passport/captcha/captcha-check";
        // 请求参数
        URI uri = new URIBuilder(checkURL)
                .setParameter("callback", paramsCallback)
                .setParameter("answer", markResult)
                .setParameter("rand", "sjrand")
                .setParameter("login_site", "E")
                .setParameter("_", timeValue)
                .build();
        // 创建请求对象
        HttpGet checkHttpGet = Tools.setRequestHeader(new HttpGet(uri), true, false, false);
        // 创建请求结果对象
        CloseableHttpResponse response = null;
        // 创建返回结果对象
        CheckCaptchaReturnResult checkCaptchaReturnResult = new CheckCaptchaReturnResult();
        try{
            // 执行请求
            response = this.session.execute(checkHttpGet);
            if (response.getStatusLine().getStatusCode() == 200){
                String responseText = Tools.responseToString(response);
                // System.out.println(originalResult);
                responseText = responseText.substring(responseText.indexOf("(")+1, responseText.length()-2);
                JSONObject jsonData = JSONObject.parseObject(responseText);
                int resultCode = jsonData.getIntValue("result_code");
                if (resultCode == 4){
                    checkCaptchaReturnResult.setStatus(true);
                    checkCaptchaReturnResult.setSession(this.session);
                    return checkCaptchaReturnResult;
                }
            }
        }
        finally {
            if (response != null){
                response.close();
            }
            // this.httpClient.close();
        }
        checkCaptchaReturnResult.setStatus(false);
        checkCaptchaReturnResult.setSession(this.session);
        return checkCaptchaReturnResult;
    }

    /**
     * 生成一个用于之后检查验证码的字符串
     * @return 检查验证码的字符串
     */
    private static String getCheckCode(){
        Random random = new Random();
        String checkCode = "jQuery1910";
        StringBuilder stringBuilder = new StringBuilder();
        for (int i=0; i<16; i++){
            // checkCode += random.nextInt(9);
            stringBuilder.append(random.nextInt(9));
        }
        checkCode += stringBuilder;
        return checkCode;
    }

    /**
     *      如果验证码返回格式为 xml 则用这个进行解析
     *
     * @param responseText      返回数据text
     * @return                  验证码base64
     */
    private static String getCaptchaBase64FromXml(String responseText){
        try {
            // File inputFile = new File(testStr);
            InputStream inputStream = new ByteArrayInputStream(responseText.getBytes(StandardCharsets.UTF_8));
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder;
            dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputStream);
            doc.getDocumentElement().normalize();
            XPath xPath =  XPathFactory.newInstance().newXPath();
            String expression = "/HashMap/image";
            NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(doc, XPathConstants.NODESET);
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node nNode = nodeList.item(i);
                return nNode.getTextContent();
            }
        } catch (ParserConfigurationException e) {
            logger.info("返回xml格式数据，但是解析错误");
        } catch (SAXException e) {
            logger.info("返回xml格式数据，但是解析错误");
        } catch (XPathExpressionException e) {
            logger.info("返回xml格式数据，但是xpath错误");
        }catch (IOException e){
            logger.info("返回xml格式数据，但是解析错误");
        }
        return "";
    }

    /**
     *      如果验证码返回格式为 json 则用这个进行解析
     *
     * @param responseText      返回数据text
     * @return                  验证码base64
     */
    private static String getCaptchaBase64FromJson(String responseText){
        // 处理返回文本为json格式字符串
        String jsonStr = responseText.substring(responseText.indexOf("(")+1, responseText.length()-2);
        // 创建Json对象从返回的文本
        JSONObject jsonData = JSONObject.parseObject(jsonStr);
        // 获取返回结果, 验证码base64字符串, 结果信息, 结果码
        String captchaBase64Str = jsonData.getString("image");
        String resultMsg  = jsonData.getString("result_message");
        String resultCode = jsonData.getString("result_code");
        // 正确返回的结果, 用于匹配返回结果
        String trueCode = "0";
        String trueMsg  = "生成验证码成功";
        // 生成验证码成功
        if (resultCode.equals(trueCode) && resultMsg.equals(trueMsg)){
            return captchaBase64Str;
        }
        return "";
    }
}

/**
 *  返回结构
 *  status，result，timeValue, paramsCallback
 */
class GetCaptchaReturnResult{

    private Boolean status;
    private String  result;
    private String  timeValue;
    private String  parmasCallback;

    // Get
    public Boolean getStatus() {
        return status;
    }

    public String getResult() {
        return result;
    }

    public String getTimeValue() {
        return timeValue;
    }

    public String getParmasCallback() {
        return parmasCallback;
    }
    // Set
    public void setStatus(Boolean status) {
        this.status = status;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public void setTimeValue(String timeValue) {
        this.timeValue = timeValue;
    }

    public void setParmasCallback(String parmasCallback) {
        this.parmasCallback = parmasCallback;
    }

}

/**
 *  返回结构
 *  status, result
 */
class MarkCaptchaReturnResult{

    private Boolean status;
    private String  result;

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

}

/**
 * 返回结构
 * status, session
 */
class CheckCaptchaReturnResult{

    public CloseableHttpClient getSession() {
        return session;
    }

    public void setSession(CloseableHttpClient session) {
        this.session = session;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    private CloseableHttpClient session;
    private Boolean status;

}

/**
 * return: session
 */
class ReturnSession{
    public CloseableHttpClient getReturnSession() {
        return returnSession;
    }

    public void setReturnSession(CloseableHttpClient returnSession) {
        this.returnSession = returnSession;
    }

    private CloseableHttpClient returnSession;
}