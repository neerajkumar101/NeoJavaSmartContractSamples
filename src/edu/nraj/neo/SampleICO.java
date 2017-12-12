package edu.nraj.neo;

import java.math.*;
import org.neo.smartcontract.framework.*;
import org.neo.smartcontract.framework.services.neo.*;
import org.neo.smartcontract.framework.services.neo.Runtime;
import org.neo.smartcontract.framework.services.neo.TransactionOutput;
import org.neo.smartcontract.framework.services.system.*;

public class SampleICO extends SmartContract {
    //Token Settings
    public static final String Name = "NeoToken";
    public static final String Symbol = "NTC";
    
    public static final byte[] Owner = { 47, 60, (byte) 170, 33, (byte) 216, 40, (byte) 148, 2, (byte) 242, (byte) 150, 9, 84, (byte) 154, 50, (byte) 237, (byte) 160, 97, 90, 55, (byte) 183 };
    public static byte Decimals = 8;
    private static final long factor = 100000000; //decided by Decimals()
    private static final Long neo_decimals = new Long(100000000);

    //ICO Settings
    private static final byte[] neo_asset_id = { (byte) 155, 124, (byte) 255, (byte) 218, (byte) 166, 116, (byte) 190, (byte) 174, 15, (byte) 147, 14, (byte) 190, 96, (byte) 133, (byte) 175, (byte) 144, (byte) 147, (byte) 229, (byte) 254, 86, (byte) 179, 74, 92, 34, 12, (byte) 205, (byte) 207, 110, (byte) 252, 51, 111, (byte) 197 };
    private static final Long total_amount = 100000000 * factor; // total token amount
    private static final Long pre_ico_cap = 30000000 * factor; // pre ico token amount
    private static final Long basic_rate = 1000 * factor;
    private static final int ico_start_time = 1506787200;
    private static final int ico_end_time = 1538323200;

    [DisplayName("transfer")]
    public static event Action<byte[], byte[], BigInteger> Transferred;

    [DisplayName("refund")]
    public static event Action<byte[], BigInteger> Refund;

    public static Object main(String operation, Object[] args)
    {
        if (Runtime.trigger() == TriggerType.Verification)
        {
            if (Owner.length == 20)
            {
                // if param Owner is script hash
                return Runtime.checkWitness(Owner);
            }
            else if (Owner.length == 33)
            {
                // if param Owner is public key
//                byte[] signature = operation.AsByteArray();
            	
            	byte[] signature = operation.getBytes(); //not sure if this will work or not
            	
                return verifySignature(signature, Owner);
            }
        }
        else if (Runtime.trigger() == TriggerType.Application)
        {
            if (operation == "deploy") return deploy();
            if (operation == "mintTokens") return mintTokens();
            if (operation == "totalSupply") return totalSupply();
            if (operation == "name") return Name;
            if (operation == "symbol") return Symbol;
            if (operation == "transfer")
            {
                if (args.length != 3) return false;
                byte[] from = (byte[])args[0];
                byte[] to = (byte[])args[1];
                BigInteger value = (BigInteger)args[2];
                return transfer(from, to, value);
            }
            if (operation == "balanceOf")
            {
                if (args.length != 1) return 0;
                byte[] account = (byte[])args[0];
                return balanceOf(account);
            }
            if (operation == "decimals") return Decimals;
        }
        //you can choice refund or not refund
        byte[] sender = getSender();
        long contribute_value = getContributeValue();
        if (contribute_value > 0 && sender.length != 0)
        {
            Refund(sender, contribute_value);
        }
        return false;
    }

    // initialization parameters, only once
    public static boolean deploy()
    {
        byte[] total_supply = Storage.get(Storage.currentContext(), "totalSupply");
        if (total_supply.length != 0) 
        	return false;
        
        final byte pre_ico_cap_bytes[] = { pre_ico_cap.byteValue() };
        Storage.put(Storage.currentContext(), Owner, pre_ico_cap_bytes);
        
        Storage.put(Storage.currentContext(), "totalSupply", pre_ico_cap_bytes);
        Transferred(null, Owner, pre_ico_cap);
        return true;
    }

    // The function MintTokens is only usable by the chosen wallet
    // contract to mint a number of tokens proportional to the
    // amount of neo sent to the wallet contract. The function
    // can only be called during the tokenswap period
    public static boolean mintTokens()
    {
        byte[] sender = getSender();
        // contribute asset is not neo
        if (sender.length == 0)
        {
            return false;
        }
        long contribute_value = getContributeValue();
        // the current exchange rate between ico tokens and neo during the token swap period
        long swap_rate = currentSwapRate();
        // crowdfunding failure
        if (swap_rate == 0)
        {
            Refund(sender, contribute_value);
            return false;
        }
        // you can get current swap token amount
        Long token = currentSwapToken(sender, contribute_value, swap_rate);
        BigInteger bigIntToken = new BigInteger(token.toString());
        
        if (token == 0)
        {
            return false;
        }
        // crowdfunding success
        BigInteger balance = new BigInteger(Storage.get(Storage.currentContext(), sender));
        Storage.put(Storage.currentContext(), sender,  bigIntToken.add(balance));
        BigInteger totalSupply = new BigInteger(Storage.get(Storage.currentContext(), "totalSupply"));
        Storage.put(Storage.currentContext(), "totalSupply", bigIntToken.add(totalSupply));
        Transferred(null, sender, token);
        return true;
    }

    // get the total token supply
    public static BigInteger totalSupply() {
        return new BigInteger(Storage.get(Storage.currentContext(), "totalSupply"));
    }

    // function that is always called when someone wants to transfer tokens.
    public static boolean transfer(byte[] from, byte[] to, BigInteger value) {
        if (value <= 0) return false;
        if (!Runtime.checkWitness(from)) return false;
        if (from == to) return true;
        BigInteger from_value = new BigInteger(Storage.get(Storage.currentContext(), from));
        if (from_value < value) return false;
        if (from_value == value)
            Storage.delete(Storage.currentContext(), from);
        else
            Storage.put(Storage.currentContext(), from, from_value.subtract(value));
        BigInteger to_value = new BigInteger(Storage.get(Storage.currentContext(), to));
        Storage.put(Storage.currentContext(), to, to_value.add(value));
        Transferred(from, to, value);
        return true;
    }

    // get the account balance of another account with address
    public static BigInteger balanceOf(byte[] address) {
        return new BigInteger(Storage.get(Storage.currentContext(), address))
    }

    // The function CurrentSwapRate() returns the current exchange rate
    // between ico tokens and neo during the token swap period
    private static long currentSwapRate() {
        final int ico_duration = ico_end_time - ico_start_time;
        int now = Blockchain.getHeader(Blockchain.height()).timestamp() + 15;
        int time = (int)now - ico_start_time;
        if (time < 0)
        {
            return 0;
        }
        else if (time < ico_duration)
        {
            return basic_rate;
        }
        else
        {
            return 0;
        }
    }

    //whether over contribute capacity, you can get the token amount
    private static long currentSwapToken(byte[] sender, Long value, Long swap_rate) {
        Long token = value / neo_decimals * swap_rate;
        BigInteger swapRateTimesNeoDecimals = new BigInteger(new Long(swap_rate * neo_decimals).toString());
        BigInteger bigIntToken = new BigInteger(token.toString());
        BigInteger bigIntTotalAmount = new BigInteger(total_amount.toString());
        BigInteger total_supply = new BigInteger(Storage.get(Storage.currentContext(), "totalSupply"));
        
        BigInteger balance_token = bigIntTotalAmount.subtract(total_supply);
        
        if (balance_token <= 0)
        {
            Refund(sender, value);
            return 0;
        }
        else if (balance_token < bigIntToken)
        {
            Refund(sender, (bigIntToken.subtract(balance_token)).divide(swapRateTimesNeoDecimals));
            
            token = balance_token.longValueExact();
        }
        return token;
    }

    // check whether asset is neo and get sender script hash
    private static byte[] getSender() {
        Transaction tx = (Transaction)ExecutionEngine.scriptContainer();
        TransactionOutput[] reference = tx.references();
        // you can choice refund or not refund
        for (TransactionOutput output : reference)
        {
            if (output.assetId() == neo_asset_id) return output.scriptHash();
        }
        return new byte[0];
    }

    // get smart contract script hash
    private static byte[] getReceiver() {
        return ExecutionEngine.executingScriptHash();
    }

    // get all you contribute neo amount
    private static long getContributeValue() {
        Transaction tx = (Transaction)ExecutionEngine.scriptContainer();
        TransactionOutput[] outputs = tx.outputs();
        long value = 0;
        // get the total amount of Neo
        for (TransactionOutput output : outputs)
        {
            if (output.scriptHash() == getReceiver() && output.assetId() == neo_asset_id)
            {
                value += (long)output.value();
            }
        }
        return value;
    }
}

