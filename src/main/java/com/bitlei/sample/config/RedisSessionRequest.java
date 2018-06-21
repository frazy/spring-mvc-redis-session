/**
 * 
 */
package com.bitlei.sample.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;

import org.apache.commons.collections4.iterators.IteratorEnumeration;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 使用Redis作为session属性持久化.
 * 
 * @author hzyinglei
 *
 */
public class RedisSessionRequest extends HttpServletRequestWrapper {
    private static final Logger logger = LoggerFactory.getLogger(RedisSessionRequest.class);

    private HttpServletResponse response;
    private RedisHttpSession session;
    private RedisTemplate<String, Object> redisTemplate;

    public RedisSessionRequest(HttpServletRequest request, HttpServletResponse response, RedisTemplate<String, Object> redisTemplate) {
        super(request);
        this.response = response;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public HttpSession getSession(boolean create) {
        if (session != null) {
            return session;
        }

        if (create) {
            session = new RedisHttpSession((HttpServletRequest) super.getRequest(), response, redisTemplate);

        }
        return session;
    }

    @Override
    public HttpSession getSession() {
        return getSession(true);
    }

    public static class RedisHttpSession implements HttpSession {
        private static final String COOKIE = "demo_sid";
        private static final String DOMAIN = "127.0.0.1";

        private static final String KEY_CREATION_TIME = "____creationTime";
        private static final String KEY_LAST_ACCESSED_TIME = "____lastAccessedTime";
        private static final String KEY_MAX_INACTIVE_INTERVAL = "____maxInactiveInterval";

        private HttpServletRequest request;
        private RedisTemplate<String, Object> redisTemplate;

        private String id;
        private boolean isNew = false;
        private long creationTime;
        private long lastAccessedTime;
        private int maxInactiveInterval; // seconds

        public RedisHttpSession(HttpServletRequest request, HttpServletResponse response, RedisTemplate<String, Object> redisTemplate) {
            this.request = request;
            this.redisTemplate = redisTemplate;

            // 获取cookie
            if (request.getCookies() != null) {
                Cookie cookie = Stream.of(request.getCookies()).filter(e -> COOKIE.equalsIgnoreCase(e.getName())).findFirst().orElse(null);
                if (cookie != null) {
                    this.id = cookie.getValue();
                }
            }
            if (StringUtils.isBlank(id)) {
                this.id = request.getRequestedSessionId();
                if (StringUtils.isBlank(id)) {
                    this.id = UUID.randomUUID().toString().replace("-", "").toUpperCase();
                }
            }

            // 加载or新建
            if (!hasSession(response)) {
                this.isNew = true;
                this.creationTime = System.currentTimeMillis();
                this.lastAccessedTime = creationTime;
                this.setMaxInactiveInterval((int) TimeUnit.HOURS.toSeconds(3)); // 设置3小时

                Map<String, Object> map = new HashMap<String, Object>();
                map.put(KEY_CREATION_TIME, this.creationTime);
                map.put(KEY_LAST_ACCESSED_TIME, this.lastAccessedTime);
                map.put(KEY_MAX_INACTIVE_INTERVAL, this.maxInactiveInterval);
                redisTemplate.boundHashOps(id).putAll(map);

                addCookie(response);
            }
        }

        private boolean hasSession(HttpServletResponse response) {
            List<Object> values = redisTemplate.boundHashOps(id)
                    .multiGet(Arrays.asList(KEY_CREATION_TIME, KEY_LAST_ACCESSED_TIME, KEY_MAX_INACTIVE_INTERVAL));
            if (values != null && values.size() == 3) {
                if (values.get(0) == null || values.get(1) == null || values.get(2) == null) {
                    return false;
                }

                this.isNew = false;
                this.creationTime = (long) values.get(0);
                this.lastAccessedTime = (long) values.get(1);
                this.maxInactiveInterval = (int) values.get(2);

                // 如果currentTimeMillis-lastAccessedTime大于maxInactiveInterval/10，更新lastAccessedTime
                if ((System.currentTimeMillis() - lastAccessedTime) > maxInactiveInterval * 1000 / 10) {
                    // logger.debug("session values={}", new Object[] { values });
                    this.lastAccessedTime = System.currentTimeMillis();
                    this.setAttribute(KEY_LAST_ACCESSED_TIME, lastAccessedTime);
                    this.setMaxInactiveInterval(maxInactiveInterval);

                    addCookie(response);
                }

                return true;
            }

            return false;
        }

        private void addCookie(HttpServletResponse response) {
            Cookie cookie = new Cookie(COOKIE, id);
            cookie.setDomain(DOMAIN);
            cookie.setPath("/");
            cookie.setMaxAge(this.maxInactiveInterval);
            cookie.setHttpOnly(true);
            // cookie.setSecure(true);
            response.addCookie(cookie);
        }

        @Override
        public long getCreationTime() {
            return creationTime;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public long getLastAccessedTime() {
            return lastAccessedTime;
        }

        @Override
        public ServletContext getServletContext() {
            return request.getServletContext();
        }

        @Override
        public void setMaxInactiveInterval(int interval) {
            this.maxInactiveInterval = interval;
            redisTemplate.boundHashOps(id).expire(interval > 0 ? interval : 0, TimeUnit.SECONDS);
            logger.debug("session expire {}={}", new Object[] { id, interval });
        }

        @Override
        public int getMaxInactiveInterval() {
            return maxInactiveInterval;
        }

        @SuppressWarnings("deprecation")
        @Override
        public HttpSessionContext getSessionContext() {
            throw new RuntimeException("Not support getSessionContext().");
        }

        @Override
        public Object getAttribute(String name) {
            Object value = redisTemplate.boundHashOps(id).get(name);
            logger.debug("session get {}.{}={}", new Object[] { id, name, value });
            return value;
        }

        @Override
        public Object getValue(String name) {
            return getAttribute(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            Set<String> keys = new HashSet<String>();
            Optional.ofNullable(redisTemplate.boundHashOps(id).keys()).orElse(Collections.emptySet()).forEach(key -> {
                keys.add((String) key);
            });
            return new IteratorEnumeration<String>(keys.iterator());
        }

        @Override
        public String[] getValueNames() {
            Set<String> keys = new HashSet<String>();
            Optional.ofNullable(redisTemplate.boundHashOps(id).keys()).orElse(Collections.emptySet()).forEach(key -> {
                keys.add((String) key);
            });
            return keys.toArray(new String[] {});
        }

        @Override
        public void setAttribute(String name, Object value) {
            redisTemplate.boundHashOps(id).put(name, value);
            logger.debug("session set {}.{}={}", new Object[] { id, name, value });
        }

        @Override
        public void putValue(String name, Object value) {
            setAttribute(name, value);
        }

        @Override
        public void removeAttribute(String name) {
            redisTemplate.boundHashOps(id).delete(name);
            logger.debug("session del {}.{}", new Object[] { id, name });
        }

        @Override
        public void removeValue(String name) {
            removeAttribute(name);
        }

        @Override
        public void invalidate() {
            redisTemplate.delete(id);
            logger.debug("session invalidate {}", new Object[] { id });
        }

        @Override
        public boolean isNew() {
            return isNew;
        }

    }

}
