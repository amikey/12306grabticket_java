import com.alibaba.fastjson.JSONObject;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import javax.tools.Tool;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *      登陆模块
 *
 *              方法流程：
 *              1. 获取验证码
 *              2. 标记验证码
 *              3. 检查验证码
 *              4. 登陆
 *              5. 获取登陆token
 *              6. 验证登陆token
 *              7. 登陆成功，获取用户名（不是登陆用户名）
 *
 */
public class Login {

    private CloseableHttpClient session;
    private String username;
    private String password;
    private String loginTk = "";
    // private static String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36";

    // 设置日志记录
    private static final Logger logger = LoggerFactory.getLogger(Login.class);

    // 构造器
    Login(CloseableHttpClient session, String username, String password){
        this.session  = session;
        this.username = username;
        this.password = password;
    }

    /**
     *      登陆
     *      这个请求可能会返回一个false但是message为“密码错误”的结果，如果返回了“密码错误”这个结果
     *   应当立即停止执行之后的请求，因为如果密码错误次数超过4次将会锁定账户
     *
     * @param markResult    验证码标记结果     格式：123,123,123
     *
     * @return              LoginReturnResult 对象
     *                      status, message, uamtk
     *                      布尔     字符串    字符串
     *
     * @throws Exception    异常处理
     */
    public LoginReturnResult login(String markResult) throws Exception{
        String loginURL = "https://kyfw.12306.cn/passport/web/login";
        HttpPost loginRequest = Tools.setRequestHeader(new HttpPost(loginURL), true, false, false);
        // 创建请求数据
        Map<String, String> loginData = new HashMap<>();
        loginData.put("username", this.username);
        loginData.put("password", this.password);
        loginData.put("appid", "otn");
        loginData.put("anwser", markResult);
        // 设置提交表单
        loginRequest.setEntity(Tools.doPostData(loginData));
        CloseableHttpResponse response = null;
        LoginReturnResult loginReturnResult = new LoginReturnResult();
        try{
            response = this.session.execute(loginRequest);
            if (response.getStatusLine().getStatusCode() == 200){
                String responseText = Tools.responseToString(response);
                JSONObject loginResultJsonData = JSONObject.parseObject(responseText);
                int resultCode = loginResultJsonData.getIntValue("result_code");
                // 如果密码错误则返回false, 和"密码错误"
                if (resultCode==1){
                    loginReturnResult.setStatus(false);
                    loginReturnResult.setMessage("密码错误");
                    loginReturnResult.setUamtk("");
                    return loginReturnResult;
                }
                // 登陆成功
                if (resultCode==0){
                    loginReturnResult.setStatus(true);
                    loginReturnResult.setMessage(loginResultJsonData.getString("result_message"));
                    loginReturnResult.setUamtk(loginResultJsonData.getString("uamtk"));
                    return loginReturnResult;
                }else{
                    loginReturnResult.setStatus(false);
                    loginReturnResult.setMessage(loginResultJsonData.getString("result_message"));
                    loginReturnResult.setUamtk("");
                    return loginReturnResult;
                }
            }
        }
        finally {
            if (response!=null){
                response.close();
            }
        }
        loginReturnResult.setStatus(false);
        loginReturnResult.setMessage("登陆失败");
        loginReturnResult.setUamtk("");
        return loginReturnResult;
    }

    /**
     *      获取登陆 Token
     *
     * @return              GetLoginTkReturnResult 对象
     *                      status, message, loginTk
     *                      布尔    字符串    字符串
     * @throws Exception
     */
    public GetLoginTkReturnResult getLoginTk() throws Exception{
        String getLoginTkURL = "https://kyfw.12306.cn/passport/web/auth/uamtk";
        HttpPost getLoginTkRequest = Tools.setRequestHeader(new HttpPost(getLoginTkURL), true, false, false);
        // 创建请求表单
        Map<String, String> getLoginTkData = new HashMap<>();
        getLoginTkData.put("appid", "otn");
        getLoginTkRequest.setEntity(Tools.doPostData(getLoginTkData));
        // 声明response变量
        CloseableHttpResponse response = null;
        // 创建返回结果对象
        GetLoginTkReturnResult getLoginTkReturnResult = new GetLoginTkReturnResult();
        try{
            response = this.session.execute(getLoginTkRequest);
            // 如果status_code=200
            if(response.getStatusLine().getStatusCode()==200){
                String responseText = Tools.responseToString(response);
                JSONObject getLoginTkResultJsonData = JSONObject.parseObject(responseText);
                int resultCode = getLoginTkResultJsonData.getIntValue("result_code");
                // 验证通过
                if (resultCode==0){
                    this.loginTk = getLoginTkResultJsonData.getString("newapptk");
                    getLoginTkReturnResult.setStatus(true);
                    getLoginTkReturnResult.setMessage(getLoginTkResultJsonData.getString("result_message"));
                    getLoginTkReturnResult.setLoginTk(getLoginTkResultJsonData.getString("newapptk"));
                    return getLoginTkReturnResult;
                }else{
                    getLoginTkReturnResult.setStatus(false);
                    getLoginTkReturnResult.setMessage(getLoginTkResultJsonData.getString("result_message"));
                    getLoginTkReturnResult.setLoginTk("");
                    return getLoginTkReturnResult;
                }
            }
        }
        finally {
            if(response!=null){
                response.close();
            }
        }
        getLoginTkReturnResult.setStatus(false);
        getLoginTkReturnResult.setMessage("验证失败");
        getLoginTkReturnResult.setLoginTk("");
        return getLoginTkReturnResult;

    }

    /**
     *      验证 loginTk
     *
     * @return              CheckLoginTkReturnResult 对象
     *                      status, message, username(不是登陆用户名), apptk
     *                      布尔    字符串    字符串                   字符串
     * @throws Exception
     */
    public CheckLoginTkReturnResult checkLoginTk() throws Exception{
        String checkLoginTkURL = "https://kyfw.12306.cn/otn/uamauthclient";
        HttpPost checkLoginTkRequest = Tools.setRequestHeader(new HttpPost(checkLoginTkURL), true, false, false);
        Map<String, String> checkLoginTkData = new HashMap<>();
        checkLoginTkData.put("tk", this.loginTk);
        checkLoginTkRequest.setEntity(Tools.doPostData(checkLoginTkData));
        CloseableHttpResponse response = null;
        CheckLoginTkReturnResult checkLoginTkReturnResult = new CheckLoginTkReturnResult();
        try{
            response = this.session.execute(checkLoginTkRequest);
            if (response.getStatusLine().getStatusCode()==200){
                String responseText = Tools.responseToString(response);
                JSONObject checkLoginTkJsonData = JSONObject.parseObject(responseText);
                int resultCode = checkLoginTkJsonData.getIntValue("result_code");
                if (resultCode==0){
                    checkLoginTkReturnResult.setStatus(true);
                    checkLoginTkReturnResult.setMessage(checkLoginTkJsonData.getString("result_message"));
                    checkLoginTkReturnResult.setLoginUsername(checkLoginTkJsonData.getString("username"));
                    checkLoginTkReturnResult.setApptk(checkLoginTkJsonData.getString("apptk"));
                    return checkLoginTkReturnResult;
                }else{
                    checkLoginTkReturnResult.setStatus(false);
                    checkLoginTkReturnResult.setMessage(checkLoginTkJsonData.getString("result_message"));
                    checkLoginTkReturnResult.setLoginUsername("");
                    checkLoginTkReturnResult.setApptk("");
                    return checkLoginTkReturnResult;
                }
            }
        }
        finally {
            if(response!=null){
                response.close();
            }
        }
        checkLoginTkReturnResult.setStatus(false);
        checkLoginTkReturnResult.setMessage("验证失败");
        checkLoginTkReturnResult.setLoginUsername("");
        checkLoginTkReturnResult.setApptk("");
        return checkLoginTkReturnResult;
    }

    /**
     *      检查用户登陆状态
     *      传进一个session，判断是否处于登陆状态
     *
     * @param session           session
     * @return                  T --> true
     *                          F --> false
     * @throws Exception        异常处理
     */
    public static boolean checkUserStatus(CloseableHttpClient session) throws Exception{
        String checkUserURL = "https://kyfw.12306.cn/otn/login/checkUser";
        HttpPost checkUserRequest = Tools.setRequestHeader(new HttpPost(checkUserURL), true, true, false);
        Map<String, String> checkUserStatusData = new HashMap<>();
        checkUserStatusData.put("_json_att", "");
        checkUserRequest.setEntity(Tools.doPostDataFromJson(checkUserStatusData));
        CloseableHttpResponse response = null;
        try{
            response = session.execute(checkUserRequest);
            if (response.getStatusLine().getStatusCode() == 200){
                String responseText = Tools.responseToString(response);
                boolean status = JSONObject.parseObject(responseText).getBoolean("status");
                boolean flag   = JSONObject.parseObject(responseText)
                        .getJSONObject("data")
                        .getBoolean("flag");
                // 响应成功
                if (status && flag){
                    return true;
                }
            }
        }
        finally {
            if(response!=null){
                response.close();
            }
        }
        return false;
    }

    /**
     *      测试输入的用户名和密码是否能登陆
     *
     * @param username      12306用户名
     * @param password      12306密码
     * @return              TestLoginReturnResult 对象
     *                      status, message
     */
    public static TestLoginReturnResult testLogin(String username, String password) throws Exception{

        // 创建测试会话
        CloseableHttpClient session = Tools.getSession(30000);

        // 创建验证码实例用于获取，标记，检查验证码
        Captcha captcha = new Captcha(session);

        // 创建获取验证码对象
        GetCaptchaReturnResult getCaptchaReturnResult;
        getCaptchaReturnResult = captcha.getCaptcha();
        if (!getCaptchaReturnResult.getStatus()){
            return testLoginReturnResult(false, "获取验证码失败");
        }

        // 创建获取标记对象
        MarkCaptchaReturnResult markCaptchaReturnResult;
        markCaptchaReturnResult = captcha.markCaptchaV2(getCaptchaReturnResult.getResult());
        if (!markCaptchaReturnResult.getStatus()){
            return testLoginReturnResult(false, "识别验证码失败");
        }

        // 创建检查验证码对象
        CheckCaptchaReturnResult checkCaptchaReturnResult;
        checkCaptchaReturnResult = captcha.checkCaptcha(
                getCaptchaReturnResult.getParmasCallback(),
                markCaptchaReturnResult.getResult(),
                getCaptchaReturnResult.getTimeValue());
        if (!checkCaptchaReturnResult.getStatus()){
            return testLoginReturnResult(false, "检查验证码失败");
        }
        // 验证成功提取会话
        session = checkCaptchaReturnResult.getSession();

        // 创建登陆实例
        Login login = new Login(session, username, password);

        // 尝试登陆
        LoginReturnResult loginReturnResult;
        loginReturnResult = login.login(markCaptchaReturnResult.getResult());
        if (!loginReturnResult.getStatus()) {
            return testLoginReturnResult(false, loginReturnResult.getMessage());
        }

        // 获取登陆tk
        GetLoginTkReturnResult getLoginTkReturnResult;
        getLoginTkReturnResult = login.getLoginTk();
        if (!getLoginTkReturnResult.getStatus()){
            return testLoginReturnResult(false, getLoginTkReturnResult.getMessage());
        }

        // 验证登陆tk
        CheckLoginTkReturnResult checkLoginTkReturnResult;
        checkLoginTkReturnResult = login.checkLoginTk();
        if (!checkLoginTkReturnResult.getStatus()){
            return testLoginReturnResult(false, checkLoginTkReturnResult.getMessage());
        }

        //登陆成功
        return testLoginReturnResult(true, "登陆成功");
    }
    private static TestLoginReturnResult testLoginReturnResult(Boolean status, String message){
        TestLoginReturnResult testLoginReturnResult = new TestLoginReturnResult();
        testLoginReturnResult.setStatus(status);
        testLoginReturnResult.setMessage(message);
        return testLoginReturnResult;
    }

    /**
     *      这个方法包含了登陆相关的所有逻辑
     *
     *      在创建了Login实例之后直接调用该方法即可
     *
     * @return      LoginMethodReturnResutl 对象
     *              status， session
     */
    public LoginMethodReturnResutl loginMethod() throws Exception{
        // 创建验证码实例用于获取，标记，检查验证码
        Captcha captcha = new Captcha(this.session);


        // 创建获取验证码对象
        logger.info("获取验证码......");
        GetCaptchaReturnResult getCaptchaReturnResult;
        try{
            getCaptchaReturnResult = captcha.getCaptcha();
            // 获取验证码失败停止
            if (!getCaptchaReturnResult.getStatus()){
                logger.info("获取验证码失败！");
                return loginMethodReturnResutlFalse();
            }
        }catch (Exception e){
            logger.info("获取验证码超时！");
            return loginMethodReturnResutlFalse();
        }


        // 创建获取标记对象
        logger.info("识别验证码......");
        MarkCaptchaReturnResult markCaptchaReturnResult;
        try{
            markCaptchaReturnResult = captcha.markCaptchaV2(getCaptchaReturnResult.getResult());
            // 标记失败停止
            if (!markCaptchaReturnResult.getStatus()){
                // 公共服务器标记失败用自建服务器标记
                markCaptchaReturnResult = captcha.markCaptchaV1(getCaptchaReturnResult.getResult());
                if (!markCaptchaReturnResult.getStatus()){
                    logger.info("识别验证码失败！");
                    return loginMethodReturnResutlFalse();
                }
            }
        }catch (Exception e){
            logger.info("识别验证码超时！");
            return loginMethodReturnResutlFalse();
        }


        // 创建检查验证码对象
        logger.info("验证验证码......");
        CheckCaptchaReturnResult checkCaptchaReturnResult;
        try {
            checkCaptchaReturnResult = captcha.checkCaptcha(
                    getCaptchaReturnResult.getParmasCallback(),
                    markCaptchaReturnResult.getResult(),
                    getCaptchaReturnResult.getTimeValue());
            // 检查失败退出
            if (!checkCaptchaReturnResult.getStatus()){
                logger.info("验证验证码失败！");
                return loginMethodReturnResutlFalse();
            }
        } catch (Exception e) {
            logger.info("验证验证码超时！");
            return loginMethodReturnResutlFalse();
        }

        // 验证成功提取会话
        this.session = checkCaptchaReturnResult.getSession();

        // 创建登陆实例
        // Login login = new Login(main.session, main.username, main.password);

        // 尝试登陆
        logger.info("正在登陆......");
        LoginReturnResult loginReturnResult;
        try {
            loginReturnResult = login(markCaptchaReturnResult.getResult());
            // 如果返回密码错误则要立即停止, 否则4次错误会锁定账户
            String passwordError = "密码错误";
            if (loginReturnResult.getMessage().equals(passwordError)){
                logger.info("密码错误！");
                return loginMethodReturnResutlFalse();
            }
            if (!loginReturnResult.getStatus()) {
                logger.info("登陆失败！");
                return loginMethodReturnResutlFalse();
            }
        } catch (Exception e) {
            logger.info("登陆超时！");
            return loginMethodReturnResutlFalse();
        }

        // 获取登陆tk
        logger.info("获取登陆token......");
        GetLoginTkReturnResult getLoginTkReturnResult;
        try {
            getLoginTkReturnResult = getLoginTk();
            if (!getLoginTkReturnResult.getStatus()){
                logger.info("获取登陆token失败");
                return loginMethodReturnResutlFalse();
            }
        } catch (Exception e) {
            logger.info("获取登陆token超时");
            return loginMethodReturnResutlFalse();
        }

        // 验证登陆yk
        logger.info("验证登陆token......");
        CheckLoginTkReturnResult checkLoginTkReturnResult;
        try {
            checkLoginTkReturnResult = checkLoginTk();
            if (!checkLoginTkReturnResult.getStatus()){
                logger.info("验证登陆token失败!");
                return loginMethodReturnResutlFalse();
            }
        } catch (Exception e) {
            logger.info("验证登陆token超时!");
            return loginMethodReturnResutlFalse();
        }
        String loginInfo = "登陆成功!    用户名：" + checkLoginTkReturnResult.getLoginUsername();
        logger.info(loginInfo);

        //登陆成功
        LoginMethodReturnResutl loginMethodReturnResutl = new LoginMethodReturnResutl();
        loginMethodReturnResutl.setStatus(true);
        loginMethodReturnResutl.setSession(this.session);
        return loginMethodReturnResutl;
    }
    private LoginMethodReturnResutl loginMethodReturnResutlFalse(){
        LoginMethodReturnResutl loginMethodReturnResutl = new LoginMethodReturnResutl();
        loginMethodReturnResutl.setStatus(false);
        loginMethodReturnResutl.setSession(this.session);
        return loginMethodReturnResutl;
    }

}

/**
 * return:  status, message, uamtk
 */
class LoginReturnResult{
    public boolean getStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUamtk() {
        return uamtk;
    }

    public void setUamtk(String uamtk) {
        this.uamtk = uamtk;
    }

    private boolean status;
    private String  message;
    private String  uamtk;
}

/**
 * return:  status, message, loginTk
 */
class GetLoginTkReturnResult{
    public boolean getStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getLoginTk() {
        return loginTk;
    }

    public void setLoginTk(String loginTk) {
        this.loginTk = loginTk;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    private boolean status;
    private String  loginTk;
    private String  message;
}

/**
 * return:  status, message, loginUsername, apptk
 */
class CheckLoginTkReturnResult{
    public boolean getStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getLoginUsername() {
        return loginUsername;
    }

    public void setLoginUsername(String loginUsername) {
        this.loginUsername = loginUsername;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getApptk() {
        return apptk;
    }

    public void setApptk(String apptk) {
        this.apptk = apptk;
    }

    private boolean status;
    private String  loginUsername;
    private String  message;
    private String  apptk;
}

/**
 * return:  status, session
 */
class LoginMethodReturnResutl{
    public CloseableHttpClient getSession() {
        return session;
    }

    public void setSession(CloseableHttpClient session) {
        this.session = session;
    }

    public boolean getStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    private CloseableHttpClient session;
    private boolean status;
}

/**
 *  return: status, message
 */
class TestLoginReturnResult{
    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    private Boolean status;
    private String  message;

}