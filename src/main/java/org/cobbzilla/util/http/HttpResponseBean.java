package org.cobbzilla.util.http;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.cobbzilla.util.json.JsonUtil;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class HttpResponseBean {

    @Getter @Setter private int status;
    @Getter @Setter private Multimap<String, String> headers;
    @Getter private byte[] entity;
    @Getter @Setter private long contentLength;
    @Getter @Setter private String contentType;

    public void addHeader(String name, String value) {
        if (headers == null) headers = LinkedListMultimap.create();
        headers.put(name, value);
    }

    public void setEntity (InputStream entity) {
        try {
            this.entity = entity == null ? null : IOUtils.toByteArray(entity);
        } catch (IOException e) {
            throw new IllegalStateException("setEntity: error reading stream: "+e, e);
        }
    }

    public String getEntityString () {
        return entity == null ? null : new String(entity);
    }

    public <T> T getEntity (Class<T> clazz) {
        return entity == null ? null : JsonUtil.fromJsonOrDie(getEntityString(), clazz);
    }

}
