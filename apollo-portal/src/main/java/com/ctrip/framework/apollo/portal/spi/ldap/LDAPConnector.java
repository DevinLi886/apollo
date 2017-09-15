package com.ctrip.framework.apollo.portal.spi.ldap;

import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPInterface;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * @author liguochao.
 * @create 2017/9/15
 */
public class LDAPConnector {

    private static final Logger log = LoggerFactory.getLogger(LDAPConnector.class);

    private static final String HOST = "";
    private static final String BIND_DN = "";
    private static final String PASSWORD = "";
    private static final int PORT = 389;
    private static final String EMAIL_SUFFIX = "";
    private static final String BASE_DN = "";
    private static LDAPInterface ldapInterface = null;

    static {
        ldapInterface = (LDAPInterface) Proxy.newProxyInstance(LDAPConnection.class.getClassLoader(), new Class[]{LDAPInterface.class},
                new LDAPConnectorProxy());
    }

    static class LDAPConnectorProxy implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            LDAPConnection ldapConnection = null;
            Object result = null;
            try {
                ldapConnection = new LDAPConnection(HOST, PORT, BIND_DN, PASSWORD);
                result = method.invoke(ldapConnection, args);
            } catch (Exception e) {
                log.error("call ldapConnection method error,method={}", method.getName(), e);
            } finally {
                if (ldapConnection != null) {
                    ldapConnection.close();
                }
            }
            return result;
        }
    }

    /**
     * 用户登陆
     *
     * @return 用户信息
     */
    public static UserInfo login(String userName, String password) throws Exception {
        SearchResult result = fetchByUserName(userName);
        String dn = getDN(result, userName);
        if (StringUtils.isEmpty(dn)) {
            throw new Exception("不存在的用户!");
        }

        try {
            new LDAPConnection(HOST, PORT, dn, password);
        } catch (Exception e) {
            throw new Exception("账号或密码不正确");
        }
        return findUser(result, userName);
    }

    /**
     * 查询已经知道的用户集合
     *
     * @param userIds 多个用户userId
     * @return 返回查询的用户信息集合
     */
    public static ArrayList<UserInfo> findUsers(List<String> userIds) throws Exception {
        ArrayList<UserInfo> res = new ArrayList<>();
        SearchResult result = fetchAll("cn");
        List<SearchResultEntry> searchEntries = result.getSearchEntries();
        if (searchEntries == null) return res;
        for (SearchResultEntry entry : searchEntries) {
            if (entry != null) {
                for (String userId : userIds) {
                    String mail = entry.getAttributeValue("mail");
                    if (StringUtils.isNotBlank(mail) && StringUtils.equals(userId + EMAIL_SUFFIX, mail)) {
                        UserInfo userInfo = new UserInfo();
                        userInfo.setUserId(userId);
                        userInfo.setName(entry.getAttributeValue("cn"));
                        userInfo.setEmail(mail);
                        res.add(userInfo);
                    }
                }
            }
        }
        return res;
    }

    /**
     * 根据条件查找用户
     *
     * @param keyword 搜索关键字
     * @param offset  偏移
     * @param limit   查询多少个返回
     * @return 查找的用户集
     */
    public static ArrayList<UserInfo> findUsers(String keyword, int offset, int limit) throws Exception {
        ArrayList<UserInfo> res = new ArrayList<>();
        SearchResult result = fetchAll();
        if (result == null) return res;
        List<SearchResultEntry> searchEntries = result.getSearchEntries();
        int totalCount = 0;
        for (int i = offset; i < searchEntries.size(); i++) {
            SearchResultEntry entry = searchEntries.get(i);
            if (res.size() >= limit) return res;
            if (entry == null)
                continue;
            String displayName = entry.getAttributeValue("displayName");
            String accountName = entry.getAttributeValue("uid");

            if (displayName.contains(keyword) || accountName.contains(keyword)) {
                totalCount = totalCount + 1;
                if (totalCount >= offset) {
                    UserInfo userInfo = new UserInfo();
                    userInfo.setUserId(accountName);
                    userInfo.setName(displayName);
                    userInfo.setEmail(entry.getAttributeValue("mail"));
                    res.add(userInfo);
                }
            }
        }
        return res;
    }

    /**
     * @param userId 用户id
     * @return 返回用户信息
     */
    public static UserInfo findUser(String userId) throws Exception {
        UserInfo userInfo = new UserInfo();
        String mail = userId + EMAIL_SUFFIX;
        SearchResult searchResult = fetchByUserName(userId);
        String name = getCN(searchResult, mail);
        userInfo.setEmail(mail);
        userInfo.setUserId(userId);
        userInfo.setName(name);
        return userInfo;
    }

    /**
     * 获取LDAP中所有用户信息
     */
    private static SearchResult fetchAll(String... attributes) throws Exception {
        String baseDN = "ou=development,ou=people,dc=kuaikanmanhua,dc=com";
        String filter = "objectClass=organizationalPerson";
        return ldapInterface.search(baseDN, SearchScope.SUB, filter, attributes);
    }

    /**
     * 查询用户的dn
     */
    private static String getDN(SearchResult searchResult, String userName) throws Exception {
        if (searchResult == null) {
            return null;
        }

        for (SearchResultEntry searchResultEntry : searchResult.getSearchEntries()) {
            String username = searchResultEntry.getAttributeValue("uid");
            if (StringUtils.equals(username, userName)) {
                return searchResultEntry.getDN();
            }
        }
        return null;
    }

    private static SearchResult fetchByUserName(String userName) throws Exception {
        String filter = "uid=" + userName;
        return ldapInterface.search(BASE_DN, SearchScope.SUB, filter);
    }

    /**
     * @param userId 用户id
     * @return 返回用户信息
     */
    private static UserInfo findUser(SearchResult searchResult, String userId) throws Exception {
        UserInfo userInfo = new UserInfo();
        String mail = userId + EMAIL_SUFFIX;
        String name = getCN(searchResult, mail);
        userInfo.setEmail(mail);
        userInfo.setUserId(userId);
        userInfo.setName(name);
        return userInfo;
    }

    /**
     * 根据用户的mail获取用户名
     */
    private static String getCN(SearchResult searchResult, String mail) throws Exception {
        if (searchResult == null) {
            return "";
        }
        List<SearchResultEntry> searchEntries = searchResult.getSearchEntries();
        for (SearchResultEntry entry : searchEntries) {
            String value_mail = entry.getAttributeValue("mail");
            if (StringUtils.isNotEmpty(value_mail) && StringUtils.equals(value_mail, mail))
                return entry.getAttributeValue("displayName");

        }
        return "";
    }
}
