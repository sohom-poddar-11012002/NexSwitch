package com.nexswitch.adapters.inbound.iso8583;

import org.jpos.iso.ISOException;
import org.jpos.iso.packager.GenericPackager;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

// LEARN: SingletonPackager — GenericPackager parses XML once at startup; shared read-only across all Netty handler instances
@Component
public class Iso8583PackagerFactory {

    private static final String PACKAGER_CLASSPATH = "cfg/acquiring-packager.xml";

    private final GenericPackager packager;

    public Iso8583PackagerFactory() {
        try (InputStream in = Iso8583PackagerFactory.class.getClassLoader()
                .getResourceAsStream(PACKAGER_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException(
                    "acquiring-packager.xml not found on classpath at: " + PACKAGER_CLASSPATH);
            }
            packager = new GenericPackager(in);
        } catch (ISOException e) {
            throw new IllegalStateException("Failed to parse ISO 8583 packager XML", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read ISO 8583 packager XML", e);
        }
    }

    public GenericPackager getPackager() {
        return packager;
    }
}
