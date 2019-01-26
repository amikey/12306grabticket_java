import com.alibaba.fastjson.JSONObject;

import com.nexmo.client.NexmoClient;
import com.nexmo.client.NexmoClientException;
import com.nexmo.client.auth.AuthMethod;
import com.nexmo.client.auth.TokenAuthMethod;
import com.nexmo.client.sms.SmsSubmissionResponseMessage;
import com.nexmo.client.sms.messages.TextMessage;
import com.nexmo.client.sms.SmsSubmissionResponse;
import com.nexmo.client.voice.Call;
import com.nexmo.client.voice.CallEvent;
import com.nexmo.client.voice.StreamResponse;

import java.net.URI;
import java.net.URISyntaxException;
// import com.twilio.rest.api.v2010.account.Call;
import com.twilio.Twilio;
// import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

import com.sun.mail.util.MailSSLSocketFactory;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;

import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.net.URI;

public class SendMessage {

    /**
     *      默认发件人设置
     */
    private static final String HOST = ConfigInfo.getSenderHost();
    private static final String PORT = ConfigInfo.getSenderPort();
    private static final String USERNAME = ConfigInfo.getSenderUsername();
    private static final String PASSWORD = ConfigInfo.getSenderPassword();
    private static final String SENDER = ConfigInfo.getSenderEmail();

    /**
     *      设置日志记录
     */
    private static final Logger logger = LoggerFactory.getLogger(SendMessage.class);

    /**
     *      测试代码
     */
    public static void main(String[] args) {
        testSendEmail();
    }
    /**
     *      发送通知, 仅发送邮件
     *
     * @param emailSubject      邮件标题
     * @param emailContent      邮件内容
     * @return                  true, false
     */
    public static boolean sendNotify(String emailSubject,
                                     String emailContent){
        String[] receiverEmail = ConfigInfo.getReceiverEmail();
        String senderEmail = ConfigInfo.getSenderEmail();
        String senderHost = ConfigInfo.getSenderHost();
        String senderPort = ConfigInfo.getSenderPort();
        String senderUsername = ConfigInfo.getSenderUsername();
        String senderPassword = ConfigInfo.getSenderPassword();
        boolean emailStatus = false;
        emailStatus = SendMessage.sendEmailText(senderHost, senderPort, senderUsername, senderPassword, senderEmail, receiverEmail, emailSubject, emailContent, true);
        return emailStatus;
    }
    /**
     *      发送通知, 通知方式从配置类读取, 所以只需要填写发送方式需要用到的参数即可, 其他填空值
     *
     *      例如:
     *           邮件 --------------> emailSubject, emailContent, "", ""
     *           邮件, sms ---------> emailSubject, emailContent, smsText, ""
     *           邮件, sms, phone --> emailSubject, emailContent, smsText, soundXmlUrl
     *           sms --------------> "", "", smsText, ""
     *           sms, phone -------> "", "", smsText, soundXmlUrl
     *           phone ------------>"", "", "", soundXmlUrl
     *
     * @param emailSubject      邮件标题
     * @param emailContent      邮件内容
     * @param smsText           短信内容
     * @param soundXmlUrl       语音播放xml文件url
     * @return                  true. false
     */
    public static boolean sendNotify(String emailSubject,
                                     String emailContent,
                                     String smsText,
                                     String soundXmlUrl){

        // 读取发送方式, phone, sms, email
        String[] sendMode = ConfigInfo.getNotifyMode();
        boolean isPhone = Arrays.asList(sendMode).contains("phone");
        boolean isSms = Arrays.asList(sendMode).contains("sms");
        boolean isEmail = Arrays.asList(sendMode).contains("email");

        Boolean platformStatus = null;
        Boolean emailStatus = null;
        // 语音或短信存在其一调用平台发送方法
        if (isPhone || isSms){
            platformStatus = phoneSmsPlatform(smsText, soundXmlUrl);
        }
        // 发邮件
        if (isEmail){
            String[] receiverEmail = ConfigInfo.getReceiverEmail();
            String senderEmail = ConfigInfo.getSenderEmail();
            String senderHost = ConfigInfo.getSenderHost();
            String senderPort = ConfigInfo.getSenderPort();
            String senderUsername = ConfigInfo.getSenderUsername();
            String senderPassword = ConfigInfo.getSenderPassword();
            emailStatus = SendMessage.sendEmailText(senderHost, senderPort, senderUsername, senderPassword, senderEmail, receiverEmail, emailSubject, emailContent, true);
        }
        // 调用平台和邮件
        if (platformStatus!= null && emailStatus != null){
            if (platformStatus && emailStatus){
                return true;
            }
        }
        // 仅调用平台
        if (platformStatus != null){
            if (platformStatus){
                return true;
            }
        }
        // 仅调用邮件
        if (emailStatus != null){
            if (emailStatus){
                return true;
            }
        }
        return false;
    }

    /**
     *      语音,短信平台方法
     *      发送方式从配置文件读取, 只要填写使用的发送方式需要提供的内容即可, 其他填空值
     *
     *      例如:
     *           短信 --------> smsText, ""
     *           语音 --------> "", soundXmlUrl
     *           短信, 语音 --> smsText, soundXmlUrl
     *
     * @param smsText           smsText
     * @param soundXmlUrl       soundXmlUrl
     * @return                  true, false
     */
    private static boolean phoneSmsPlatform(String smsText, String soundXmlUrl){
        // 从 ConfigInfo 类读取配置
        // 平台名从配置类读取
        String platformName = ConfigInfo.getPhoneSmsPlatformName();
        String[] sendMode = ConfigInfo.getNotifyMode();
        String accountSid = ConfigInfo.getAccoutSid();
        String authToken = ConfigInfo.getAuthToken();
        String from = ConfigInfo.getFROM();
        String[] tos = ConfigInfo.getTOS();
        // String soundXmlUrl = ConfigInfo.getSoundXmlUrl();
        // twilio 平台
        if ("twilio".equals(platformName.toLowerCase())){
            boolean smsStatus = false;
            boolean phoneStatus= false;

            boolean isPhone = Arrays.asList(sendMode).contains("phone");
            boolean isSms = Arrays.asList(sendMode).contains("sms");
            boolean isPhoneSms = isPhone && isSms;
            // 语音和短信
            if (isPhoneSms){
                phoneStatus = sendPhoneVoiceNotifyFromTwilio(accountSid, authToken, from, tos, soundXmlUrl);
                smsStatus = sendSMSNotifyFromTwilio(accountSid, authToken, from, tos, smsText);
                if (smsStatus && phoneStatus){
                    return true;
                }
            }
            // 仅语音
            if (isPhone){
                 phoneStatus = sendPhoneVoiceNotifyFromTwilio(accountSid, authToken, from, tos, soundXmlUrl);
                 if (phoneStatus){
                     return true;
                 }
            }
            // 仅短信
            if (isSms){
                 smsStatus = sendSMSNotifyFromTwilio(accountSid, authToken, from, tos, smsText);
                 if (smsStatus){
                     return true;
                 }
            }
        }
        return false;
    }
    /**
     *      Twilio 语音接口
     *
     * @param accountSid        accountSid
     * @param authToken         authToken
     * @param from              from
     * @param tos               tos
     * @param configPath        configPath --> soundUrl
     * @return                  true, false
     */
    private static boolean sendPhoneVoiceNotifyFromTwilio(String accountSid, String authToken, String from, String[] tos, String configPath){
        Twilio.init(accountSid, authToken);
        com.twilio.rest.api.v2010.account.Call call = null;
        for (String to:tos) {
            try {
                call = com.twilio.rest.api.v2010.account.Call.creator(new PhoneNumber(to), new PhoneNumber(from),
                        new URI(configPath)).create();
            } catch (URISyntaxException e) {
                return false;
            }
            if (!"QUEUED".equals(call.getStatus().name())){
                return false;
            }
        }
        return true;
    }
    /**
     *      Twilio 短信接口
     *
     * @param accountSid    accountSid
     * @param authToken     authToken
     * @param from          from
     * @param tos           tos(数组)
     * @param text          text 短信内容, 超过长度将会拆分
     * @return              true, false
     */
    private static boolean sendSMSNotifyFromTwilio(String accountSid, String authToken, String from, String[] tos, String text){
        Twilio.init(accountSid, authToken);
        for (String to:tos) {
            com.twilio.rest.api.v2010.account.Message message = com.twilio.rest.api.v2010.account.Message
                                                                    .creator(
                                                                            // to
                                                                            new PhoneNumber(to),
                                                                            // from
                                                                            new PhoneNumber(from),
                                                                            text)
                                                                    .create();
            if (!"QUEUED".equals(message.getStatus().name())){
                return false;
            }
        }
        return true;
    }

    /**
     *      Nexmo 短信接口, 单人
     *
     * @param apiKey        accountSid
     * @param apiSecret     authToken
     * @param from          from
     * @param to            to
     * @param smsText       smsText
     * @return              true, false
     */
    private static boolean sendSMSNotifyFromNexmo(String apiKey, String apiSecret, String from, String to, String smsText){
        try {
            NexmoClient client = new NexmoClient.Builder()
                    .apiKey(apiKey)
                    .apiSecret(apiSecret)
                    .build();
            SmsSubmissionResponse responses = client.getSmsClient().submitMessage(
                    new TextMessage(
                            // 可以用文字, 例如: 12306ticket
                            from,
                            // 格式: 8615935582121
                            to,
                            smsText,
                            true
                    )
            );
            for (SmsSubmissionResponseMessage response : responses.getMessages()) {
                if (!"OK".equals(response.getStatus().name())){
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    /**
     *      Nexmo 短信接口, 多人
     *
     * @param apiKey        accountSid
     * @param apiSecret     authToken
     * @param from          from
     * @param tos           tos
     * @param smsText       smsText
     * @return              true, false
     */
    private static boolean sendSMSNotifyFromNexmo(String apiKey, String apiSecret, String from, String[] tos, String smsText){
        try {
            NexmoClient client = new NexmoClient.Builder()
                    .apiKey(apiKey)
                    .apiSecret(apiSecret)
                    .build();
            for (String to: tos) {
                SmsSubmissionResponse responses = client.getSmsClient().submitMessage(
                        new TextMessage(
                                // 可以用文字, 例如: 12306ticket
                                from,
                                // 格式: 8615935582121
                                to,
                                smsText,
                                true
                        )
                );
                for (SmsSubmissionResponseMessage response : responses.getMessages()) {
                    if (!"OK".equals(response.getStatus().name())){
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    private static boolean sendPhoneVoiceNotifyYunzhixin(String[] phoneNums, String templateId, String appCode){
        try {
            for (String phoneNum: phoneNums) {
                CloseableHttpClient session = Tools.getSession(30000);
                URI url = new URIBuilder("http://yzxyytz.market.alicloudapi.com/yzx/voiceNotifySms")
                        .setParameter("phone", phoneNum)
                        .setParameter("templateId", templateId)
                        .setParameter("variable", "variable")
                        .build();
                HttpPost request = new HttpPost(url);
                request.addHeader("Authorization", "APPCODE " + appCode);
                CloseableHttpResponse response = null;
                try{
                    response = session.execute(request);
                    if (response.getStatusLine().getStatusCode() == 200){
                        String responseText = Tools.responseToString(response);
                        String returnCode = JSONObject.parseObject(responseText).getString("return_code");
                        if ("00000".equals(returnCode)){
                            return true;
                        }else{
                            logger.info("错误号：" + returnCode);
                            return false;
                        }
                    }
                }catch (Exception e){
                    return false;
                }
            }
        } catch (URISyntaxException e) {
            return false;
        }
        return false;
    }
    /**
     *      从默认邮箱发送邮件
     *
     * @param receiver      收件人
     * @param subject       标题
     * @param text          内容
     * @return              T --> true
     *                      F --> false
     */
    public static boolean sendEmailTextFromDefault(String receiver, String subject, String text){
        boolean ssl = true;
        return sendEmailText(HOST, PORT, USERNAME, PASSWORD, SENDER, receiver, subject, text, ssl);
    }
    public static boolean sendEmailTextFromDefault(String[] receivers, String subject, String text){
        boolean ssl = true;
        return sendEmailText(HOST, PORT, USERNAME, PASSWORD, SENDER, receivers, subject, text, ssl);
    }
    /**
     *      测试邮箱是否能收到通知
     */
    private static void testSendEmail(){
        String subject = "12306抢票通知测试";
        String content = "如果您能收到这封邮件说明测试通过";
        sendEmailTextFromDefault(ConfigInfo.getReceiverEmail(), subject, content);
    }
    public static boolean sendEmailHtmlFromDefault(String receiver, String subject, String text){
        boolean ssl = true;
        return sendEmailHtml(HOST, PORT, USERNAME, PASSWORD, SENDER, receiver, subject, text, ssl);
    }
    public static boolean sendEmailHtmlFromDefault(String[] receivers, String subject, String text){
        boolean ssl = true;
        return sendEmailHtml(HOST, PORT, USERNAME, PASSWORD, SENDER, receivers, subject, text, ssl);
    }
    /**
     *      发送纯文本邮件
     *
     * @param host      邮件主机
     * @param port      端口, ssl->465
     * @param username  邮箱用户名
     * @param password  邮箱密码
     * @param sender    发件邮箱
     * @param receiver  收件邮箱
     * @param subject   标题
     * @param text      发送内容
     * @param ssl       是否用ssl
     * @return          true, false
     */
    public static boolean sendEmailText(String host,
                                        String port,
                                        String username,
                                        String password,
                                        String sender,
                                        String receiver,
                                        String subject,
                                        String text,
                                        boolean ssl){
        try {
            Properties properties = getProperties(host, port, ssl);
            Session session = getSession(properties);
            Transport transport = getTransport(session, host, username, password);
            MimeMessage message = getMessage(session, sender, receiver, subject, text, false);
            try{
                transport.sendMessage(message, message.getAllRecipients());
            }
            catch (MessagingException e){
                return false;
            }
            finally {
                transport.close();
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     *      发送纯文本邮件，多个收件人
     *
     * @param host      邮件主机
     * @param port      端口, ssl->465
     * @param username  邮箱用户名
     * @param password  邮箱密码
     * @param sender    发件邮箱
     * @param receivers 收件邮箱列表
     * @param subject   标题
     * @param text      发送内容
     * @param ssl       是否用ssl
     * @return          true, false
     */
    public static boolean sendEmailText(String host,
                                        String port,
                                        String username,
                                        String password,
                                        String sender,
                                        String[] receivers,
                                        String subject,
                                        String text,
                                        boolean ssl){
        try {
            for (String receiver: receivers){
                Properties properties = getProperties(host, port, ssl);
                Session session = getSession(properties);
                Transport transport = getTransport(session, host, username, password);
                MimeMessage message = getMessage(session, sender, receiver, subject, text, false);
                try{
                    transport.sendMessage(message, message.getAllRecipients());
                }
                catch (MessagingException e){
                    return false;
                }
                finally {
                    transport.close();
                }
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static boolean sendEmailHtml(String host,
                                        String port,
                                        String username,
                                        String password,
                                        String sender,
                                        String receiver,
                                        String subject,
                                        String text,
                                        boolean ssl){
        try {
            Properties properties = getProperties(host, port, ssl);
            Session session = getSession(properties);
            Transport transport = getTransport(session, host, username, password);
            String htmlText = setSendHtmlTemplate(text);
            MimeMessage message = getMessage(session, sender, receiver, subject, htmlText, true);
            try{
                transport.sendMessage(message, message.getAllRecipients());
            }
            catch (MessagingException e){
                return false;
            }
            finally {
                transport.close();
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static boolean sendEmailHtml(String host,
                                        String port,
                                        String username,
                                        String password,
                                        String sender,
                                        String[] receivers,
                                        String subject,
                                        String text,
                                        boolean ssl){
        try {
            for (String receiver: receivers) {
                Properties properties = getProperties(host, port, ssl);
                Session session = getSession(properties);
                Transport transport = getTransport(session, host, username, password);
                String htmlText = setSendHtmlTemplate(text);
                MimeMessage message = getMessage(session, sender, receiver, subject, htmlText, true);
                try{
                    transport.sendMessage(message, message.getAllRecipients());
                }
                catch (MessagingException e){
                    return false;
                }
                finally {
                    transport.close();
                }
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }
    /**
     *      转换订票结果到html源码
     *
     * @param content       订票结果
     * @return              html源码
     */
    private static String setSendHtmlTemplate(String content){
        String[] line = content.split("\n");
        // html模板
        String htmlContent = Tools.readFileText("./SendNotifyHTMLTemplate/sendHtmlTemplate.html");
        List<String> result = new ArrayList<>();
        for (int i = 1; i < line.length; i++){
            String[] value = line[i].split("：");
            result.add(value[1]);
        }
        htmlContent = htmlContent.replace("{SequenceNo}", result.get(0));
        htmlContent = htmlContent.replace("{PassengerIdTypeName}", result.get(1));
        htmlContent = htmlContent.replace("{PassengerIdNo}", result.get(2));
        htmlContent = htmlContent.replace("{PassengerName}", result.get(3));
        htmlContent = htmlContent.replace("{CoachName}", result.get(4));
        htmlContent = htmlContent.replace("{SeatName}", result.get(5));
        htmlContent = htmlContent.replace("{SeatTypeName}", result.get(6));
        htmlContent = htmlContent.replace("{FromStationName}", result.get(7));
        htmlContent = htmlContent.replace("{ToStationName}", result.get(8));
        htmlContent = htmlContent.replace("{StationTrainCode}", result.get(9));
        htmlContent = htmlContent.replace("{StartTrainDate}", result.get(10));
        htmlContent = htmlContent.replace("{TicketPrice}", result.get(11));
        htmlContent = htmlContent.replace("{TicketNo}", result.get(12));
        htmlContent = htmlContent.replace("{TicketTypeName}", result.get(13));
        return htmlContent;
    }
    private static Properties getProperties(String host, String port, boolean ssl) throws Exception{
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.host", host);
        properties.setProperty("mail.smtp.port", port);
        properties.setProperty("mail.transport.protocol", "smtp");
        properties.setProperty("mail.smtp.auth", "true");
        // Debug 用
        // properties.setProperty("mail.debug", "true");

        // 开启SSL
        if (ssl){
            MailSSLSocketFactory mailSSLSocketFactory = new MailSSLSocketFactory();
            mailSSLSocketFactory.setTrustAllHosts(true);
            properties.put("mail.smtp.ssl.enable", "true");
            properties.put("mail.smtp.ssl.socketFactory", mailSSLSocketFactory);
        }
        return properties;
    }
    private static Session getSession(Properties properties) throws Exception{
        return Session.getInstance(properties);
    }
    private static Transport getTransport(Session session, String host, String username, String password) throws Exception{
        // session.setDebug(true);
        Transport transport = session.getTransport();
        transport.connect(host, username, password);
        return transport;
    }
    private static MimeMessage getMessage(Session session, String sender, String receiver, String subject, String text, boolean isHtml) throws Exception{
        // 创建邮件对象
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(sender));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(receiver));
        message.setSubject(subject);
        if (isHtml) {
            message.setContent(text, "text/html;charset=utf-8");
        }else{
            message.setText(text);
        }
        return message;
    }
}
