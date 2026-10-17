package com.example.rag.support;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 文件内容摘要：用 SHA-256 判断同名文档两次上传的内容是否完全一致
 */
public final class ContentHash {

    private static final int BUFFER_SIZE = 8192;

    private ContentHash() {
    }

    /** 流式读取，调用方负责关闭输入流 */
    public static String of(InputStream in) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new IllegalStateException("读取文件内容失败：" + e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256 摘要", e);
        }
    }
}
