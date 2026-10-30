package cn.wildfirechat.org.shiro;

import cn.wildfirechat.org.jpa.ShiroSession;
import cn.wildfirechat.org.jpa.ShiroSessionRepository;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.shiro.session.Session;
import org.apache.shiro.session.UnknownSessionException;
import org.apache.shiro.session.mgt.SimpleSession;
import org.apache.shiro.session.mgt.eis.SessionDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class DBSessionDao implements SessionDAO {
    /**
     * 会话反序列化只允许这些类型，避免读到库里的恶意字节流时执行任意代码。
     * SimplePrincipalCollection 是 Shiro 登录后写入会话的身份信息；SavedRequest 是未登录访问受限接口时 Shiro 记下的原请求；
     * Number 是 Long/Integer 等包装类的父类，反序列化时也会被校验。
     */
    private static final Set<String> ALLOWED_CLASSES = new HashSet<>(Arrays.asList(
            "org.apache.shiro.session.mgt.SimpleSession",
            "org.apache.shiro.subject.SimplePrincipalCollection",
            "org.apache.shiro.web.util.SavedRequest",
            "java.lang.Number",
            "java.util.HashMap",
            "java.util.LinkedHashMap",
            "java.util.HashSet",
            "java.util.LinkedHashSet",
            "java.util.ArrayList",
            "java.util.Date",
            "java.lang.String",
            "java.lang.Long",
            "java.lang.Integer",
            "java.lang.Short",
            "java.lang.Byte",
            "java.lang.Character",
            "java.lang.Float",
            "java.lang.Double",
            "java.lang.Boolean"
    ));
    /** 缓存条目存活时间 */
    private static final long CACHE_TTL_MINUTES = 60;

    /** 用于脏检查：缓存已持久化的 session 序列化数据，避免无变化时重复写入数据库 */
    private final Cache<Object, byte[]> sessionDataCache = CacheBuilder.newBuilder()
            .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
            .build();
    /** 读缓存：避免同一 session 在短时间内重复读库 */
    private final Cache<Object, Session> sessionReadCache = CacheBuilder.newBuilder()
            .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
            .build();

    @Autowired
    private ShiroSessionRepository shiroSessionRepository;

    @Override
    public Serializable create(Session session) {
        String sessionId = UUID.randomUUID().toString().replaceAll("-", "");
        ((SimpleSession) session).setId(sessionId);
        // 创建时即入库，避免应用重启后 session 丢失
        update(session);
        return sessionId;
    }

    @Override
    public Session readSession(Serializable sessionId) throws UnknownSessionException {
        Session cached = sessionReadCache.getIfPresent(sessionId);
        if (cached != null) {
            return cached;
        }

        ShiroSession shiroSession = shiroSessionRepository.findById((String) sessionId).orElse(null);
        if (shiroSession != null) {
            Session session = byteToSession(shiroSession.getSessionData());
            if (session != null) {
                // 同步到缓存，用于后续脏检查比对
                sessionDataCache.put(sessionId, shiroSession.getSessionData());
                sessionReadCache.put(sessionId, session);
            }
            return session;
        }
        return null;
    }

    @Override
    public void update(Session session) throws UnknownSessionException {
        byte[] currentBytes = sessionToByte(session);
        if (currentBytes == null) {
            return;
        }
        Object sessionId = session.getId();
        byte[] cachedBytes = sessionDataCache.getIfPresent(sessionId);

        // 脏检查：如果数据未变化，直接跳过数据库写入
        if (cachedBytes != null && Arrays.equals(cachedBytes, currentBytes)) {
            return;
        }

        ShiroSession shiroSession = new ShiroSession((String) sessionId, currentBytes);
        shiroSessionRepository.save(shiroSession);
        sessionDataCache.put(sessionId, currentBytes);
        sessionReadCache.put(sessionId, session);
    }

    @Override
    public void delete(Session session) {
        Object sessionId = session.getId();
        sessionDataCache.invalidate(sessionId);
        sessionReadCache.invalidate(sessionId);
        shiroSessionRepository.deleteById((String) sessionId);
    }

    @Override
    public Collection<Session> getActiveSessions() {
        // 原 sessionMap 未被实际维护，直接返回空集合即可
        // 如需准确数据，应从数据库查询
        return java.util.Collections.emptyList();
    }

    private byte[] sessionToByte(Session session) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try {
            ObjectOutputStream oo = new ObjectOutputStream(bo);
            oo.writeObject(session);
            return bo.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Session byteToSession(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try (ObjectInputStream in = new SafeObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object object = in.readObject();
            if (object instanceof SimpleSession) {
                return (SimpleSession) object;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 只允许会话需要的类型，其它类一律拒绝。
     * 库里的 session_data 一旦被写入恶意字节流，默认的 ObjectInputStream 会直接反序列化并执行 gadget。
     */
    private static final class SafeObjectInputStream extends ObjectInputStream {
        SafeObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            String name = desc.getName();
            if (name.startsWith("[")) {
                // 数组只放行基本类型数组、String[]、Object[]
                if (name.length() == 2
                        || "[Ljava.lang.String;".equals(name)
                        || "[Ljava.lang.Object;".equals(name)) {
                    return super.resolveClass(desc);
                }
                throw new InvalidClassException("Unauthorized array class", name);
            }
            if (!ALLOWED_CLASSES.contains(name)) {
                throw new InvalidClassException("Unauthorized deserialization class", name);
            }
            return super.resolveClass(desc);
        }
    }
}
