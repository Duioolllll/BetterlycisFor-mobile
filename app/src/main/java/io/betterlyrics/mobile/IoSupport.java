package io.betterlyrics.mobile;

import java.io.*;
import java.text.*;
import java.util.*;

/** Shared bounded reads; callers retain ownership of the input stream. */
public final class IoSupport {
    private IoSupport(){}
    public static final class TooLargeException extends IOException {TooLargeException(){super("입력 크기 제한을 초과했습니다.");}}
    public static void checkInterrupted()throws InterruptedIOException {
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("작업이 취소되었습니다.");
    }
    public static byte[] read(InputStream in,int limit,long timeoutMillis)throws IOException {
        if(in==null)throw new IOException("입력을 열지 못했습니다.");
        if(limit<0||timeoutMillis<0)throw new IllegalArgumentException();
        long started=System.nanoTime();
        ByteArrayOutputStream out=new ByteArrayOutputStream(Math.min(8192,limit));
        byte[] buffer=new byte[8192];
        while(true){
            checkInterrupted();
            if(timeoutMillis>0&&(System.nanoTime()-started)/1_000_000>=timeoutMillis)throw new InterruptedIOException("응답 시간 초과");
            // Probe at most one byte beyond the limit, including unknown-length streams.
            int count=in.read(buffer,0,(int)Math.min(buffer.length,(long)limit-out.size()+1));
            checkInterrupted();
            if(timeoutMillis>0&&(System.nanoTime()-started)/1_000_000>=timeoutMillis)throw new InterruptedIOException("응답 시간 초과");
            if(count<0)return out.toByteArray();
            if(count>limit-out.size())throw new TooLargeException();
            out.write(buffer,0,count);
        }
    }
    public static String hex(byte[] bytes,boolean upper){
        char[] digits=(upper?"0123456789ABCDEF":"0123456789abcdef").toCharArray(),out=new char[bytes.length*2];
        for(int i=0;i<bytes.length;i++){int value=bytes[i]&255;out[i*2]=digits[value>>>4];out[i*2+1]=digits[value&15];}
        return new String(out);
    }
    /** RFC Retry-After delta seconds or HTTP date. Negative means invalid. */
    public static long retryAfterMillis(String value,long now){
        if(value==null||value.trim().isEmpty())return -1;value=value.trim();
        if(value.matches("[0-9]{1,9}"))try{return Long.parseLong(value)*1000;}catch(NumberFormatException ignored){return -1;}
        try{SimpleDateFormat format=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US);format.setLenient(false);ParsePosition pos=new ParsePosition(0);Date date=format.parse(value,pos);return date==null||pos.getIndex()!=value.length()?-1:Math.max(0,date.getTime()-now);}catch(IllegalArgumentException ignored){return -1;}
    }
}
