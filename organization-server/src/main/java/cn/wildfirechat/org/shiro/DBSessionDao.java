package cn.wildfirechat.org.shiro;

import cn.wildfirechat.org.jpa.ShiroSession;
import cn.wildfirechat.org.jpa.ShiroSessionRepository;
import org.apache.shiro.session.Session;
import org.apache.shiro.session.UnknownSessionException;
import org.apache.shiro.session.mgt.SimpleSession;
import org.apache.shiro.session.mgt.eis.SessionDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DBSessionDao implements SessionDAO {
    // 用于脏检查：缓存已持久化的 session 序列化数据，避免无变化时重复写入数据库
    private final Map<Object, byte[]> sessionDataCache = new ConcurrentHashMap<>();

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
        ShiroSession shiroSession = shiroSessionRepository.findById((String) sessionId).orElse(null);
        if (shiroSession != null) {
            Session session = byteToSession(shiroSession.getSessionData());
            if (session != null) {
                // 同步到缓存，用于后续脏检查比对
                sessionDataCache.put(sessionId, shiroSession.getSessionData());
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
        byte[] cachedBytes = sessionDataCache.get(sessionId);

        // 脏检查：如果数据未变化，直接跳过数据库写入
        if (cachedBytes != null && Arrays.equals(cachedBytes, currentBytes)) {
            return;
        }

        ShiroSession shiroSession = new ShiroSession((String) sessionId, currentBytes);
        shiroSessionRepository.save(shiroSession);
        sessionDataCache.put(sessionId, currentBytes);
    }

    @Override
    public void delete(Session session) {
        Object sessionId = session.getId();
        sessionDataCache.remove(sessionId);
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
        ByteArrayInputStream bi = new ByteArrayInputStream(bytes);
        try {
            ObjectInputStream in = new ObjectInputStream(bi);
            return (SimpleSession) in.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
