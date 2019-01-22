import com.sun.mail.util.MailSSLSocketFactory;

import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.twilio.Twilio;
// import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

public class SendMessage {

    public static boolean sendPhoneVoiceNotifyFromTwilio(String accountSid, String authToken, String from, String[] tos, String configPath){
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

    public static boolean sendSMSNotifyFromTwilio(String accountSid, String authToken, String from, String[] tos, String text){
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
     * @throws Exception 异常处理
     */
    public static boolean sendEmailText(String host,
                                        String port,
                                        String username,
                                        String password,
                                        String sender,
                                        String receiver,
                                        String subject,
                                        String text,
                                        boolean ssl) throws Exception{
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
        return true;
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
