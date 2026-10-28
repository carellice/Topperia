package it.topperia.game;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.FrameMetrics;
import android.view.Window;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/** Device regressions for the canvas UI, driven through real touch and Back events. */
public final class MotionInstrumentation extends Instrumentation {
    private Activity activity;
    private TopperiaView view;
    private int checks;
    private String swipeInfo="";
    private final List<Double> scrollFrames=Collections.synchronizedList(new ArrayList<>());
    private final List<Double> scrollDrawFrames=Collections.synchronizedList(new ArrayList<>());
    private final List<Double> scrollCommandFrames=Collections.synchronizedList(new ArrayList<>());
    private volatile boolean recordFrames;
    private Window.OnFrameMetricsAvailableListener frameListener;
    private float pointerScale;
    private int[] pointerLocation;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            // The runner is intended for an isolated disposable emulator.
            getTargetContext().getSharedPreferences("topperia", 0).edit()
                .remove("session").remove("ranking").remove("recorded")
                .putBoolean("onboarding_complete", true).putBoolean("reduced", false)
                .putBoolean("sound", false).putBoolean("haptic", false).commit();
            getTargetContext().getSharedPreferences("topperia_updates", 0).edit()
                .putLong("last_check", System.currentTimeMillis()).commit();
            activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            view = main(() -> findView(activity.getWindow().getDecorView()));
            check(view != null, "native canvas is present");
            pause(600);
            check("home".equals(value("screen")), "starts on home");
            screenshot("home");

            tap("Classifiche");
            check("library".equals(value("screen")), "dock opens the catalogue");
            check(value("previousPage") != null, "navigation retains the outgoing page");
            screenshot("page-transition");
            pause(500);
            check(value("previousPage") == null, "navigation releases the outgoing page when complete");
            check(number("maxScroll") > 1000, "catalogue exposes its full scroll range");
            screenshot("library-top");

            float h = number("H");
            frameListener=(window,metrics,dropped)->{if(recordFrames){scrollFrames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION)/1_000_000.0);scrollDrawFrames.add(metrics.getMetric(FrameMetrics.DRAW_DURATION)/1_000_000.0);scrollCommandFrames.add(metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)/1_000_000.0);}};
            main(()->{activity.getWindow().addOnFrameMetricsAvailableListener(frameListener,new Handler(Looper.getMainLooper()));return null;});
            recordFrames=true;
            swipe(200, h - 180, 200, 200, 170);
            float released = number("scroll");
            pause(140);
            float coasting = number("scroll");
            check(coasting > released + 15, "list continues naturally after finger release (released="+released+", after="+coasting+", max="+number("maxScroll")+", reduced="+bool("frameReduced")+", "+swipeInfo+", "+scrollerInfo()+")");
            long down = SystemClock.uptimeMillis();
            pointer(down, down, MotionEvent.ACTION_DOWN, 205, 400);
            float stopped = number("scroll");
            pause(140);
            check(Math.abs(number("scroll") - stopped) < 3, "touching the list stops the fling");
            pointer(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 205, 400);
            recordFrames=false;
            screenshot("library-scrolled");
            int visibleRows = main(() -> {
                int rows=0;
                for (Object hit : (List<?>) field(view, "hits")) {
                    String label=(String) field(hit, "label");
                    if (label.startsWith("Gioca: ")) rows++;
                }
                return rows;
            });
            check(visibleRows > 0 && visibleRows < 10, "only visible catalogue rows become controls");
            tap("Scopri");
            pause(450);
            check("home".equals(value("screen")), "dock stays usable after scrolling");

            openSettings();
            screenshot("settings-open");
            check(main(()->!view.getAccessibilityNodeProvider().createAccessibilityNodeInfo(View.NO_ID).isScrollable()), "sheet hides background accessibility scrolling");
            float behindSheet=number("scroll");
            check(main(()->!view.getAccessibilityNodeProvider().performAction(View.NO_ID,android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null)), "sheet rejects background accessibility scroll actions");
            check(number("scroll")==behindSheet,"background stays still behind the sheet");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            check("settings".equals(value("modal")) && bool("sheetClosing"), "Back retains the sheet during its exit");
            pause(350);
            check("".equals(value("modal")), "Back completes sheet dismissal");

            openSettings();
            tapPoint(15, 100);
            check(bool("sheetClosing"), "scrim tap starts dismissal");
            pause(350);
            check("".equals(value("modal")), "scrim dismissal completes");

            openSettings();
            float top = main(() -> ((Number) method(view, "sheetTop")).floatValue());
            swipe(170, top + 25, 170, top + 58, 600);
            check(!bool("sheetClosing")&&"settings".equals(value("modal")), "short downward drag keeps the sheet open");
            pause(450);
            check(main(()->((Number)method(view,"sheetProgress")).floatValue())==1&&number("sheetDrag")==0,"short drag settles smoothly back into place");
            swipe(170, top + 25, 170, top + 190, 180);
            check(bool("sheetClosing"), "dragging the handle down dismisses the sheet");
            pause(350);
            check("".equals(value("modal")), "drag dismissal completes");

            tap("Come si gioca");
            check("rules".equals(value("modal")), "rules opens its own sheet");
            pause(450);
            screenshot("rules-open");
            tap("Ci sono, giochiamo!");
            check(bool("sheetClosing"), "rules button starts the same exit motion");
            pause(350);
            check("".equals(value("modal")), "rules sheet closes completely");

            openSettings();
            tap("Animazioni ridotte, disattivo");
            check(getTargetContext().getSharedPreferences("topperia", 0).getBoolean("reduced", false), "reduced motion preference updates");
            tap("Chiudi");
            pause(100);
            check("".equals(value("modal")), "reduced motion closes without a tween");
            tap("Impostazioni");
            pause(100);
            check("settings".equals(value("modal")), "reduced motion can reopen sheets");
            check(main(() -> ((Number) method(view, "sheetProgress")).floatValue()) == 1, "reduced motion presents the complete sheet");
            tap("Animazioni ridotte, attivo");
            pause(300);
            check(!getTargetContext().getSharedPreferences("topperia", 0).getBoolean("reduced", true), "standard motion restores");
            tap("Chiudi");
            pause(350);

            openSettings();
            tap("Riguarda introduzione animata");
            pause(450);
            check("onboarding".equals(value("screen")),"settings opens the animated introduction");
            screenshot("onboarding-replay");
            tap("Salta introduzione");
            pause(450);
            check("home".equals(value("screen")),"closing introduction returns to its originating page");

            tap("Percorso");
            pause(450);
            check("profile".equals(value("screen")), "profile navigation works after modal interactions");
            screenshot("profile");
            tap("Classifiche");
            pause(450);
            tap("Categoria Italia");
            pause(450);
            check("Italia".equals(value("filter")), "category controls remain aligned with their rendering");
            check(number("scroll") == 0, "changing category resets scroll position");
            screenshot("category-italia");
            tapPrefix("Gioca: ");
            pause(500);
            check("game".equals(value("screen")),"a visible catalogue card opens its game");
            android.widget.EditText input=(android.widget.EditText)value("input");
            check(main(()->input.getVisibility()==View.VISIBLE),"native answer input appears after navigation");
            float scale=number("scale");
            float inputX=main(()->(input.getX()+input.getWidth()/2f)/scale);
            float inputY=main(()->(input.getY()+input.getHeight()/2f)/scale);
            tapPoint(inputX,inputY);
            pause(350);
            check(main(input::hasFocus),"native answer input receives a touch at its rendered position");
            sendStringSync("test");
            check(main(()->"test".equals(input.getText().toString())),"native answer input receives keyboard text");
            screenshot("game-keyboard");
            it.topperia.game.engine.GameSession session=(it.topperia.game.engine.GameSession)value("session");
            String correct=session.ranking.entries.get(0).name;
            main(()->{input.setText(correct);return null;});
            android.widget.Button submit=(android.widget.Button)value("submit");
            float submitX=main(()->(submit.getX()+submit.getWidth()/2f)/scale);
            float submitY=main(()->(submit.getY()+submit.getHeight()/2f)/scale);
            tapPoint(submitX,submitY);
            pause(700);
            check(main(()->session.currentPlayer().isRevealed(0)),"native submit reveals the correct answer");
            check(main(()->session.currentPlayer().score()==1&&session.currentPlayer().attemptsUsed()==1),"reveal preserves score and attempt rules");
            screenshot("game-reveal");
            String metrics=saveFrameMetrics();
            main(()->{activity.getWindow().removeOnFrameMetricsAvailableListener(frameListener);return null;});
            result.putString("stream", "PASS: " + checks + " Android motion UI assertions\n"+metrics+"\nScreenshots: files/qa-motion\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            StringWriter stack = new StringWriter(); error.printStackTrace(new PrintWriter(stack));
            result.putString("stream", "FAIL after " + checks + " checks\n" + stack);
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void openSettings() throws Exception {
        tap("Impostazioni");
        check("settings".equals(value("modal")), "settings starts opening");
        check(!bool("sheetClosing"), "opening sheet is interactive and not dismissing");
        pause(450);
        check(main(() -> ((Number) method(view, "sheetProgress")).floatValue()) == 1, "settings reaches its resting position");
    }

    private TopperiaView findView(View root) {
        if (root instanceof TopperiaView) return (TopperiaView) root;
        if (root instanceof ViewGroup) for (int i=0;i<((ViewGroup)root).getChildCount();i++) {
            TopperiaView found=findView(((ViewGroup)root).getChildAt(i)); if(found!=null)return found;
        }
        return null;
    }

    private void tap(String label) throws Exception {
        tapMatching(label,false);
    }
    private void tapPrefix(String prefix)throws Exception{tapMatching(prefix,true);}
    private void tapMatching(String label,boolean prefix) throws Exception {
        RectF bounds = main(() -> {
            for (Object hit : (List<?>) field(view, "hits")) {
                String actual=(String)field(hit,"label");
                if((prefix&&actual.startsWith(label))||actual.equals(label)||actual.equals(label+", selezionata"))return new RectF((RectF)field(hit,"bounds"));
            }
            throw new AssertionError("Visible control missing: "+label);
        });
        tapPoint(bounds.centerX(),bounds.centerY());
    }

    private void tapPoint(float x,float y) throws Exception {
        long start=SystemClock.uptimeMillis();
        pointer(start,start,MotionEvent.ACTION_DOWN,x,y);pause(45);
        pointer(start,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y);
    }

    private void swipe(float x,float y,float endX,float endY,int duration) throws Exception {
        // sendPointerSync synchronizes each MOVE with the window transaction and
        // can pause long enough on an emulator to erase the last velocity sample.
        // Let Android generate the continuous gesture on its own input timeline.
        float scale=number("scale");
        int[] location=main(()->{int[] point=new int[2];view.getLocationOnScreen(point);return point;});
        String command=String.format(java.util.Locale.ROOT,"input touchscreen swipe %d %d %d %d %d",Math.round(location[0]+x*scale),Math.round(location[1]+y*scale),Math.round(location[0]+endX*scale),Math.round(location[1]+endY*scale),duration);
        long start=SystemClock.uptimeMillis();
        try(java.io.InputStream output=new android.os.ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(command))){byte[] buffer=new byte[256];while(output.read(buffer)!=-1){}}
        swipeInfo="duration="+(SystemClock.uptimeMillis()-start)+"; up: "+scrollerInfo();
    }

    private String scrollerInfo()throws Exception{return main(()->{android.widget.OverScroller s=(android.widget.OverScroller)field(view,"scroller");return "finished="+s.isFinished()+", currY="+s.getCurrY()+", finalY="+s.getFinalY()+", velocity="+s.getCurrVelocity();});}

    private void pointer(long down,long time,int action,float x,float y) throws Exception {
        if(action==MotionEvent.ACTION_DOWN){pointerScale=number("scale");pointerLocation=main(() -> {int[] point=new int[2];view.getLocationOnScreen(point);return point;});}
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,pointerLocation[0]+x*pointerScale,pointerLocation[1]+y*pointerScale,0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        try{sendPointerSync(event);}finally{event.recycle();}
    }

    private void screenshot(String name) throws Exception {
        Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"captures "+name);
        if(name.equals("page-transition")){
            int visible=0,total=0;
            for(int y=bitmap.getHeight()/8;y<bitmap.getHeight()*3/4;y+=12)for(int x=bitmap.getWidth()/12;x<bitmap.getWidth()*11/12;x+=12){int color=bitmap.getPixel(x,y);if(Math.abs(((color>>16)&255)-248)+Math.abs(((color>>8)&255)-247)+Math.abs((color&255)-240)>20)visible++;total++;}
            check(visible>total/12,"navigation frame contains composed content instead of an empty flash");
        }
        File directory=new File(getTargetContext().getFilesDir(),"qa-motion");directory.mkdirs();
        try(FileOutputStream output=new FileOutputStream(new File(directory,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}finally{bitmap.recycle();}
    }

    private String saveFrameMetrics()throws Exception{
        ArrayList<Double> frames; synchronized(scrollFrames){frames=new ArrayList<>(scrollFrames);}Collections.sort(frames);
        check(!frames.isEmpty(),"collects real window frame timings during scrolling");
        double median=frames.get(frames.size()/2),p95=frames.get(Math.min(frames.size()-1,(int)(frames.size()*.95))),max=frames.get(frames.size()-1);
        int overBudget=0;for(double time:frames)if(time>16.67)overBudget++;
        ArrayList<Double> draw,commands;synchronized(scrollDrawFrames){draw=new ArrayList<>(scrollDrawFrames);}synchronized(scrollCommandFrames){commands=new ArrayList<>(scrollCommandFrames);}Collections.sort(draw);Collections.sort(commands);
        String summary=String.format(java.util.Locale.ROOT,"Scroll FrameMetrics: %d frames; total median %.2f ms, p95 %.2f ms, max %.2f ms, >16.67ms %d; draw median %.2f ms, p95 %.2f ms; command issue median %.2f ms (emulator)",frames.size(),median,p95,max,overBudget,draw.get(draw.size()/2),draw.get(Math.min(draw.size()-1,(int)(draw.size()*.95))),commands.get(commands.size()/2));
        File directory=new File(getTargetContext().getFilesDir(),"qa-motion");directory.mkdirs();
        try(PrintWriter out=new PrintWriter(new File(directory,"frame-metrics.txt"))){out.println(summary);out.println("durationMs");for(double time:frames)out.println(time);}
        return summary;
    }

    private Object value(String name)throws Exception{return main(()->field(view,name));}
    private float number(String name)throws Exception{return ((Number)value(name)).floatValue();}
    private boolean bool(String name)throws Exception{return (Boolean)value(name);}
    private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static Object method(Object target,String name)throws Exception{Method m=target.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(target);}
    private void check(boolean condition,String description){checks++;if(!condition)throw new AssertionError(description);}
    private void pause(long ms){SystemClock.sleep(ms);}
    private <T>T main(Callable<T> action)throws Exception{
        AtomicReference<T> value=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
        runOnMainSync(()->{try{value.set(action.call());}catch(Throwable error){failure.set(error);}});
        if(failure.get()!=null)throw new RuntimeException(failure.get());return value.get();
    }
}
