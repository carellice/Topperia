package it.topperia.game.ui;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Chiptune music and sound effects synthesised at startup: no audio files ship with the app.
 * Every AudioTrack call runs on one worker thread, so the UI never waits on the mixer.
 */
public final class Sound {
    public static final int TAP=0,HIT=1,MISS=2,SEAL=3,FLIP=4,WIN=5,SHUFFLE=6,SWITCH=7;
    static final int RATE=22050;
    private static final int SINE=0,SQUARE=1,TRIANGLE=2,NOISE=3;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AudioTrack[] effects=new AudioTrack[8];
    private AudioTrack music;
    private boolean musicOn=false,released=false;

    public Sound(){worker.execute(()->{
        try{
            for(int i=0;i<effects.length;i++)effects[i]=track(effect(i),false);
            music=track(loop(),true);music.setVolume(.32f);
            if(musicOn)music.play();
        }catch(RuntimeException ignored){}
    });}

    public void play(int id){worker.execute(()->{
        AudioTrack t=released?null:effects[id];if(t==null)return;
        try{t.stop();t.reloadStaticData();t.play();}catch(RuntimeException ignored){}
    });}
    public void music(boolean on){worker.execute(()->{
        musicOn=on;if(released||music==null)return;
        try{if(on)music.play();else music.pause();}catch(RuntimeException ignored){}
    });}
    public void release(){worker.execute(()->{
        released=true;for(AudioTrack t:effects)if(t!=null)t.release();if(music!=null)music.release();
    });worker.shutdown();}

    private static AudioTrack track(short[] pcm,boolean loop){
        AudioTrack t=new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(loop?AudioAttributes.CONTENT_TYPE_MUSIC:AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.length*2).build();
        t.write(pcm,0,pcm.length);
        if(loop)t.setLoopPoints(0,pcm.length,-1);
        return t;
    }

    static float hz(int midi){return (float)(440*Math.pow(2,(midi-69)/12.0));}

    /** Adds one enveloped voice; the pitch glides from f0 to f1 and the tail fades to avoid clicks. */
    static void voice(float[] out,float start,float seconds,float f0,float f1,int wave,float volume,float decay){
        int from=(int)(start*RATE),length=(int)(seconds*RATE);double phase=0;int seed=from*31+7;
        float gain=1,fall=(float)Math.exp(-1.0/(decay*RATE));
        for(int i=0;i<length&&from+i<out.length;i++,gain*=fall){
            float t=i/(float)RATE,k=i/(float)length,f=f0+(f1-f0)*k;phase+=f/RATE;double cycle=phase-Math.floor(phase);
            float sample;
            switch(wave){
                case SQUARE:sample=cycle<.25?1:-1;break;
                case TRIANGLE:sample=(float)(cycle<.5?4*cycle-1:3-4*cycle);break;
                case NOISE:seed=seed*1103515245+12345;sample=((seed>>>16)&0x7fff)/16384f-1;break;
                default:sample=(float)Math.sin(cycle*2*Math.PI);
            }
            float envelope=Math.min(1,t/.004f)*gain*Math.min(1,(length-i)/(RATE*.006f));
            out[from+i]+=sample*volume*envelope;
        }
    }
    static short[] pcm(float[] mix){short[] out=new short[mix.length];for(int i=0;i<mix.length;i++)out[i]=(short)(Math.max(-1,Math.min(1,mix[i]))*32767);return out;}

    static short[] effect(int id){
        float[] o;
        switch(id){
            case HIT:o=new float[(int)(.55f*RATE)];int[] up={72,76,79,84};for(int i=0;i<4;i++)voice(o,i*.065f,i==3?.4f:.09f,hz(up[i]),hz(up[i]),SQUARE,.16f,i==3?.18f:.08f);voice(o,.2f,.3f,hz(96),hz(96),SINE,.12f,.1f);break;
            case MISS:o=new float[(int)(.42f*RATE)];voice(o,0,.4f,330,150,TRIANGLE,.4f,.25f);voice(o,0,.4f,165,75,SQUARE,.08f,.2f);break;
            case SEAL:o=new float[(int)(.16f*RATE)];voice(o,0,.15f,520,240,SINE,.45f,.05f);voice(o,0,.03f,0,0,NOISE,.12f,.01f);break;
            case FLIP:o=new float[(int)(.08f*RATE)];voice(o,0,.07f,700,1050,SINE,.3f,.03f);break;
            case WIN:o=new float[(int)(1.1f*RATE)];int[] fan={67,72,76,79};for(int i=0;i<4;i++)voice(o,i*.11f,i==3?.7f:.12f,hz(fan[i]),hz(fan[i]),SQUARE,.14f,i==3?.35f:.1f);voice(o,.33f,.7f,hz(84),hz(84),TRIANGLE,.18f,.3f);voice(o,.33f,.7f,hz(48),hz(48),TRIANGLE,.3f,.35f);break;
            case SHUFFLE:o=new float[(int)(.75f*RATE)];for(int i=0;i<12;i++)voice(o,i*.055f,.06f,hz(60+i*2),hz(60+i*2),SQUARE,.1f,.03f);break;
            case SWITCH:o=new float[(int)(.09f*RATE)];voice(o,0,.08f,900,1400,TRIANGLE,.25f,.03f);break;
            default:o=new float[(int)(.07f*RATE)];voice(o,0,.06f,880,1320,SINE,.3f,.025f);
        }
        return pcm(o);
    }

    /** An eight-bar loop in C major (I–vi–IV–V) at 112 bpm: lead, arpeggio, bass, kick and hats. */
    static short[] loop(){
        float e=60f/112/2;int steps=64;float[] o=new float[Math.round(steps*e*RATE)];
        int[] lead={72,0,76,79,76,0,72,74, 76,0,72,69,72,0,76,0, 77,0,81,77,76,0,74,72, 74,0,79,0,77,76,74,71,
                    72,76,79,84,79,0,76,0, 81,0,79,76,72,0,69,72, 77,76,77,81,79,0,77,74, 79,0,74,0,71,74,72,0};
        int[] roots={48,45,41,43};int[][] chords={{60,64,67},{57,60,64},{53,57,60},{55,59,62}};int[] bounce={0,12,7,12};
        for(int k=0;k<steps;k++){
            float at=k*e;int bar=(k/8)%4;
            if(lead[k]>0)voice(o,at,e*.95f,hz(lead[k]),hz(lead[k]),SQUARE,.07f,.16f);
            int arp=chords[bar][k%3]+12;voice(o,at,e*.9f,hz(arp),hz(arp),SINE,.05f,.1f);
            if(k%2==0){int bass=roots[bar]+bounce[(k/2)%4];voice(o,at,e*1.9f,hz(bass),hz(bass),TRIANGLE,.2f,.3f);}
            if(k%4==0)voice(o,at,.12f,130,45,SINE,.3f,.06f);else if(k%2==1)voice(o,at,.035f,0,0,NOISE,.035f,.012f);
        }
        return pcm(o);
    }
}
