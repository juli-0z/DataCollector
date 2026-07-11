package cn.zjl.datacollector.net.tcp;

/**
 * 阅读提示：GeoDsp 0x8000 外层报文协议编解码工具：统一处理帧头、版本、长度、校验和载荷截取。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * GeoDsp_Android 同源的 0x8000 外层协议编解码器。
 *
 * <p>本类只负责“外层帧”的统一处理，不直接解释 payload 的业务含义。也就是说：
 * 这里校验帧头、版本、命令号、长度和校验值；真正的采集参数、波形、监控字段解析，
 * 由 {@code CollectionManager} 等业务类完成。</p>
 *
 * <p>帧结构按小端序排列：
 * AA 55(帧头) + 00 80(版本) + id + type + no + size + baseParity + dataParity + payload。</p>
 */
public final class   GeoDspProtocol {

    /** 协议逻辑帧头是 0x55AA，落到字节流里按小端序表现为 AA 55。 */
    public static final int HEADER = 0x55AA;
    /** 当前协议版本号，对应 GeoDsp_Android 中的 0x8000。 */
    public static final int VERSION = 0x8000;
    /** 固定头长度：2+2+2+2+4+4+4+4，再预留 4 字节结构字段，共 28 字节。 */
    public static final int HEADER_SIZE = 28;
    /** 防止异常长度字段导致一次性申请过大内存。真实设备数据一般远小于该值。 */
    public static final int MAX_PAYLOAD_SIZE = 1024 * 1024;

    public static final int TYPE_REQUEST = 0;
    public static final int TYPE_ANSWER = 1;

    public static final int MSG_DEVICE_INFO = 0x00;
    public static final int MSG_TEM_CONFIG = 0x01;
    public static final int MSG_TEM_START = 0x02;
    public static final int MSG_TEM_GET_OFF_DATA = 0x03;
    public static final int MSG_TEM_GET_SEND_DATA = 0x04;
    public static final int MSG_TEM_STOP = 0x05;
    public static final int MSG_TEM_SEND_DATA = 0x06;
    public static final int MSG_TEM_SEND_MONITOR = 0xF0;

    /** 字节流搜索时使用的小端帧头第 1 个字节。 */
    private static final byte HEADER_BYTE_0 = (byte) 0xAA;
    /** 字节流搜索时使用的小端帧头第 2 个字节。 */
    private static final byte HEADER_BYTE_1 = (byte) 0x55;
    private static final byte[] EMPTY_BYTES = new byte[0];

    private GeoDspProtocol() {
    }

    public static final class Packet {
        /** 命令/消息编号，例如开始采集、停止采集、发送监控信息等。 */
        public final int id;
        /** 报文类型：0 表示请求，1 表示响应。 */
        public final int type;
        /** 序号字段，用于现场联调时关联一次请求和对应响应。 */
        public final int no;
        /** 去掉 28 字节外层头之后的业务载荷。 */
        public final byte[] payload;
        /** 完整原始帧，诊断页会用它展示最近收到/发出的二进制内容。 */
        public final byte[] frameBytes;

        private Packet(int id, int type, int no, byte[] payload, byte[] frameBytes) {
            this.id = id;
            this.type = type;
            this.no = no;
            this.payload = payload;
            this.frameBytes = frameBytes;
        }

        public boolean isAnswer() {
            return type == TYPE_ANSWER;
        }
    }

    public static final class ParseResult {
        /** 本轮可以从接收缓冲区移除的字节数。 */
        public final int bytesConsumed;
        /** 当前数据还不够组成完整帧，需要继续等待 socket 后续字节。 */
        public final boolean needsMoreData;
        @Nullable
        public final Packet packet;
        @Nullable
        public final String error;
        @Nullable
        /** 错误帧或无效字节预览，便于诊断页显示“最后错误帧”。 */
        public final byte[] previewBytes;
        /** true 表示外层结构看起来完整，但数据校验不通过。 */
        public final boolean checksumError;

        private ParseResult(int bytesConsumed,
                            boolean needsMoreData,
                            @Nullable Packet packet,
                            @Nullable String error,
                            @Nullable byte[] previewBytes,
                            boolean checksumError) {
            this.bytesConsumed = bytesConsumed;
            this.needsMoreData = needsMoreData;
            this.packet = packet;
            this.error = error;
            this.previewBytes = previewBytes;
            this.checksumError = checksumError;
        }

        private static ParseResult needsMoreData() {
            return new ParseResult(0, true, null, null, null, false);
        }

        private static ParseResult success(int bytesConsumed, Packet packet) {
            return new ParseResult(bytesConsumed, false, packet, null, packet.frameBytes, false);
        }

        private static ParseResult consume(int bytesConsumed,
                                           @Nullable String error,
                                           @Nullable byte[] previewBytes,
                                           boolean checksumError) {
            return new ParseResult(bytesConsumed, false, null, error, previewBytes, checksumError);
        }
    }

    public static byte[] encodeRequest(int id, int no, @Nullable byte[] payload) {
        return encode(id, TYPE_REQUEST, no, payload);
    }

    public static byte[] encodeAnswer(int id, int no, @Nullable byte[] payload) {
        return encode(id, TYPE_ANSWER, no, payload);
    }

    public static byte[] encode(int id, int type, int no, @Nullable byte[] payload) {
        byte[] safePayload = payload == null ? EMPTY_BYTES : payload;
        int payloadSize = safePayload.length;
        int totalSize = HEADER_SIZE + payloadSize;
        byte[] frame = new byte[totalSize];

        // 这里逐字段写入而不是用 ByteBuffer，是为了让字段偏移和 Qt/C++ 结构体一一对应。
        writeShortLe(frame, 0, HEADER);
        writeShortLe(frame, 2, VERSION);
        writeShortLe(frame, 4, id);
        writeShortLe(frame, 6, type);
        writeIntLe(frame, 8, no);
        writeIntLe(frame, 12, payloadSize);
        writeIntLe(frame, 16, id + type + no + payloadSize);
        writeIntLe(frame, 20, calculateParityXor32(safePayload, 0, payloadSize));
        System.arraycopy(safePayload, 0, frame, HEADER_SIZE, payloadSize);
        return frame;
    }

    @Nullable
    public static Packet decode(@Nullable byte[] frame) {
        if (frame == null) {
            return null;
        }
        ParseResult result = tryParse(frame, frame.length);
        if (result.packet == null || result.bytesConsumed != frame.length) {
            return null;
        }
        return result.packet;
    }

    public static ParseResult tryParse(byte[] buffer, int length) {
        if (buffer == null || length <= 0) {
            return ParseResult.needsMoreData();
        }

        // TCP 是流式协议，可能一次收到半帧、粘包或前置噪声，所以第一步必须先找帧头。
        int headerPos = findHeader(buffer, length);
        if (headerPos < 0) {
            // 如果最后 1 字节刚好是 AA，可能是下一次读取补上 55 的半个帧头，因此保留它。
            int keep = (buffer[length - 1] == HEADER_BYTE_0) ? 1 : 0;
            int consumed = length - keep;
            if (consumed <= 0) {
                return ParseResult.needsMoreData();
            }
            return ParseResult.consume(
                    consumed,
                    String.format(Locale.US, "Discarded %d byte(s) while searching for 0x8000 frame header", consumed),
                    Arrays.copyOf(buffer, Math.min(length, 16)),
                    false);
        }

        if (headerPos > 0) {
            return ParseResult.consume(
                    headerPos,
                    String.format(Locale.US, "Discarded %d stray byte(s) before 0x8000 frame header", headerPos),
                    Arrays.copyOf(buffer, Math.min(length, headerPos)),
                    false);
        }

        if (length < HEADER_SIZE) {
            return ParseResult.needsMoreData();
        }

        // 版本不匹配时只消费 2 字节，让解析器有机会在后续字节中重新寻找新的帧头。
        int version = readUnsignedShortLe(buffer, 2);
        if (version != VERSION) {
            return ParseResult.consume(
                    2,
                    String.format(Locale.US, "Invalid 0x8000 version: 0x%04X", version),
                    Arrays.copyOf(buffer, Math.min(length, HEADER_SIZE)),
                    false);
        }

        int id = readUnsignedShortLe(buffer, 4);
        int type = readUnsignedShortLe(buffer, 6);
        int no = readIntLe(buffer, 8);
        int payloadSize = readIntLe(buffer, 12);
        if (payloadSize < 0 || payloadSize > MAX_PAYLOAD_SIZE) {
            return ParseResult.consume(
                    2,
                    String.format(Locale.US, "Invalid 0x8000 payload size: %d", payloadSize),
                    Arrays.copyOf(buffer, Math.min(length, HEADER_SIZE)),
                    false);
        }

        int totalSize = HEADER_SIZE + payloadSize;
        if (length < totalSize) {
            // 已经知道了 payload 长度，但当前缓冲区还没收齐完整帧。
            return ParseResult.needsMoreData();
        }

        // 基础校验只覆盖 id/type/no/size，用于快速发现头部字段错位或被破坏。
        int baseParity = readIntLe(buffer, 16);
        int expectedBaseParity = id + type + no + payloadSize;
        if (baseParity != expectedBaseParity) {
            return ParseResult.consume(
                    2,
                    String.format(
                            Locale.US,
                            "0x8000 base parity mismatch: expected=0x%08X actual=0x%08X",
                            expectedBaseParity,
                            baseParity),
                    Arrays.copyOf(buffer, totalSize),
                    false);
        }

        // 数据校验采用 32 位小端分组异或，和 GeoDsp_Android/Qt 侧保持一致。
        int dataParity = readIntLe(buffer, 20);
        int expectedDataParity = calculateParityXor32(buffer, HEADER_SIZE, payloadSize);
        if (dataParity != expectedDataParity) {
            return ParseResult.consume(
                    2,
                    String.format(
                            Locale.US,
                            "0x8000 data parity mismatch: expected=0x%08X actual=0x%08X",
                            expectedDataParity,
                            dataParity),
                    Arrays.copyOf(buffer, totalSize),
                    true);
        }

        byte[] frameBytes = Arrays.copyOf(buffer, totalSize);
        byte[] payload = payloadSize == 0
                ? EMPTY_BYTES
                : Arrays.copyOfRange(frameBytes, HEADER_SIZE, totalSize);
        return ParseResult.success(totalSize, new Packet(id, type, no, payload, frameBytes));
    }

    public static int calculateParityXor32(byte[] data) {
        return calculateParityXor32(data, 0, data == null ? 0 : data.length);
    }

    public static int calculateParityXor32(@Nullable byte[] data, int offset, int size) {
        if (data == null || size <= 0) {
            return 0;
        }
        int end = Math.min(data.length, offset + size);
        int parity = 0;
        int index = offset;
        // 完整的 4 字节块按 little-endian int 读取后异或。
        while (index + 4 <= end) {
            parity ^= readIntLe(data, index);
            index += 4;
        }
        if (index < end) {
            // payload 长度不是 4 的倍数时，尾部不足 4 字节按低位补齐参与异或。
            int remainder = 0;
            int shift = 0;
            while (index < end) {
                remainder |= (data[index] & 0xFF) << shift;
                shift += 8;
                index++;
            }
            parity ^= remainder;
        }
        return parity;
    }

    public static String labelForMessageId(int id) {
        switch (id) {
            case MSG_DEVICE_INFO:
                return "DEVICE_INFO";
            case MSG_TEM_CONFIG:
                return "TEM_CONFIG";
            case MSG_TEM_START:
                return "TEM_START";
            case MSG_TEM_GET_OFF_DATA:
                return "TEM_GET_OFF_DATA";
            case MSG_TEM_GET_SEND_DATA:
                return "TEM_GET_SEND_DATA";
            case MSG_TEM_STOP:
                return "TEM_STOP";
            case MSG_TEM_SEND_DATA:
                return "TEM_SEND_DATA";
            case MSG_TEM_SEND_MONITOR:
                return "TEM_SEND_MONITOR";
            default:
                return String.format(Locale.US, "MSG_0x%02X", id);
        }
    }

    public static String labelForType(int type) {
        switch (type) {
            case TYPE_ANSWER:
                return "ANSWER";
            case TYPE_REQUEST:
            default:
                return "REQUEST";
        }
    }

    private static int findHeader(byte[] buffer, int length) {
        for (int i = 0; i < length - 1; i++) {
            if (buffer[i] == HEADER_BYTE_0 && buffer[i + 1] == HEADER_BYTE_1) {
                return i;
            }
        }
        return -1;
    }

    private static int readUnsignedShortLe(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readIntLe(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private static void writeShortLe(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >>> 8) & 0xFF);
    }

    private static void writeIntLe(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }
}
