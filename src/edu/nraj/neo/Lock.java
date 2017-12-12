package edu.nraj.neo;

import org.neo.smartcontract.framework.SmartContract;
import org.neo.smartcontract.framework.services.neo.*;


public class Lock extends SmartContract{
    public static boolean main(int timestamp, byte[] pubkey, byte[] signature)
    {
        Header header = Blockchain.getHeader(Blockchain.height());
        if (timestamp > header.timestamp()) return false;
        return verifySignature(signature, pubkey);
    }
}

