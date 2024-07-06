package bgu.spl.net.impl.tftp;

public enum Opcode {
    UNDEFINED,
    RRQ,
    WRQ,
    DATA,
    ACK,
    ERROR,
    DIRQ,
    LOGRQ,
    DELRQ,
    BCAST,
    DISC;

    public static Opcode getByOrdinal(byte value) {
        int index = Byte.toUnsignedInt(value);
        if (index >= 0 && index < values().length) {
            return values()[index];

        }
        return UNDEFINED;
    }

    public static Byte getByte(Opcode opcode){
        int num = opcode.ordinal();
        return (byte) (num & 0xFF);
    }

    public static Opcode getByName(String name) {
        for (int i = 0; i < Opcode.values().length; i++) {
            if (getByOrdinal((byte) i).name().equals(name)){
                return getByOrdinal((byte) i);
            }
        }
        return UNDEFINED;
    }
}
