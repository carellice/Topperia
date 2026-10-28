package it.topperia.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.text.InputFilter;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.OverScroller;
import it.topperia.game.data.Rankings;
import it.topperia.game.engine.GameSession;
import it.topperia.game.engine.RankedList;
import it.topperia.game.engine.RankingPicker;
import it.topperia.game.update.GitHubUpdater;
import it.topperia.game.update.UpdateInfo;
import it.topperia.game.update.UpdatePromptQueue;
import it.topperia.game.ui.Motion;
import java.util.*;

/** A native, resolution-independent little game world with offline rounds. */
public final class TopperiaView extends ViewGroup {
    private static final int BG=0xfff8f7f0, INK=0xff183b32, MUTED=0xff737d72, LINE=0xffe3e5d9,
        LIME=0xffdcf58d, GREEN=0xff315d43, LILAC=0xffddd3f5, ORANGE=0xfff7be91, WHITE=0xffffffff;
    private final Paint p=new Paint(3);
    private final Paint logoPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
    private final Bitmap logo;
    private final Typeface regular=Typeface.create("sans-serif",Typeface.NORMAL), bold=Typeface.create("sans-serif",Typeface.BOLD), heavy=Typeface.create("sans-serif-black",Typeface.NORMAL);
    private final Activity activity;
    private final GameStore store;
    private final java.util.List<RankedList> rankings;
    private final ArrayList<Hit> hits=new ArrayList<>();
    private final EditText input;
    private final Button submit;
    private ToneGenerator tones;
    private final GitHubUpdater updater;
    private final UpdatePromptQueue updatePrompts;
    private UpdateInfo availableUpdate;
    private String updateStatus="Controlla se ci sono nuove versioni.";
    private boolean manualUpdateCheck=false,updateReady=false;
    private int downloadProgress=-1;
    private GameSession session;
    private String screen="home", filter="Tutte", modal="", notice="";
    private GameSession.Mode pendingMode=GameSession.Mode.SOLO;
    private float scale=1,H=820,scroll=0,maxScroll=0,hitOffset=0,inputY=0,downY,downX,lastY;
    private boolean dragging=false,recorded=false,keyboard=false,contentClip=false;
    private int accessibilityFocus=-1,hovered=-1;
    private Runnable tappedAction;
    private long entered=SystemClock.uptimeMillis(),noticeAt=0,finishedAt=0,lastSubmissionAt=0;
    private final long[] reveals=new long[10];
    private int lastHit=-1;
    private final Random random=new Random();
    private final RankingPicker picker=new RankingPicker(random);
    private final OverScroller scroller;
    private final int touchSlop,minFling,maxFling;
    private VelocityTracker velocity;
    private int activePointer=-1;
    private boolean scrollingGesture=false,sheetGesture=false,sheetClosing=false,capturing=false,frameReduced=false,frameAnimating=false;
    private long frameTime,pageAt=0,sheetAt=0,pressAt=0,filterAt=0,toggleAt=0;
    private Picture previousPage;
    private float pageDirection=1,pageShift=0,sheetFrom=0,sheetTarget=0,sheetDrag=0;
    private RectF pressedBounds;
    private float pressRelease=1;
    private String toggleKey="";
    private float toggleFrom=0;
    private final LinkedHashSet<String> categories=new LinkedHashSet<>();
    private final HashMap<String,String[]> wrappedText=new HashMap<>();
    private final HashMap<String,FittedText> fittedText=new HashMap<>();
    private final Path iconPath=new Path(),starPath=new Path(),cupPath=new Path();
    private LinearGradient dockGradient;
    private float gradientHeight=-1;
    private int onboardingStep=0,shuffleGeneration=0;
    private String onboardingReturn="home",shuffleReturn="home";
    private boolean onboardingReplay=false;
    private RankedList queuedRanking;
    private float navFrom=0,navTarget=0;
    private long navAt=0;
    private static final float DOCK_SPACE=104;

    public TopperiaView(Context context) {
        super(context); activity=(Activity)context; store=new GameStore(context); rankings=Rankings.all();
        updatePrompts=new UpdatePromptQueue(activity,this);
        scroller=new OverScroller(context);
        ViewConfiguration config=ViewConfiguration.get(context);touchSlop=config.getScaledTouchSlop();minFling=config.getScaledMinimumFlingVelocity();maxFling=config.getScaledMaximumFlingVelocity();
        categories.add("Tutte");for(RankedList ranking:rankings)categories.add(ranking.category);
        BitmapFactory.Options logoOptions=new BitmapFactory.Options();logoOptions.inScaled=false;
        logo=BitmapFactory.decodeResource(getResources(),R.drawable.topperia_logo,logoOptions);
        setWillNotDraw(false); setFocusable(true); setFocusableInTouchMode(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        input=new EditText(context);
        input.setSingleLine(true); input.setTextSize(16); input.setTextColor(INK); input.setHintTextColor(MUTED);
        input.setHint("La tua risposta…"); input.setBackgroundColor(Color.TRANSPARENT); input.setPadding(0,0,0,0);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(GameSession.MAX_INPUT_LENGTH)});
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_GO|EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setContentDescription("Scrivi un elemento della Top 10");
        input.setOnEditorActionListener((v,action,event)->{if(event!=null&&event.getAction()!=KeyEvent.ACTION_DOWN)return true;if(action==EditorInfo.IME_ACTION_GO || (event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){guess();return true;}return false;});
        addView(input);
        submit=new Button(context); submit.setText("↑"); submit.setTextSize(27); submit.setTextColor(INK);
        submit.setAllCaps(false); submit.setPadding(0,0,0,0); submit.setMinWidth(0); submit.setMinimumWidth(0); submit.setMinHeight(0); submit.setMinimumHeight(0);
        GradientDrawable button=new GradientDrawable(); button.setColor(LIME); button.setCornerRadius(dp(18)); submit.setBackground(button);
        submit.setContentDescription("Conferma risposta"); submit.setOnClickListener(v->guess()); addView(submit);
        restore();
        if(!store.option("onboarding_complete",false))screen="onboarding";
        updater=createUpdater();
        UpdateInfo cached=updater.downloadedUpdate();
        if(cached!=null){availableUpdate=cached;updateReady=true;downloadProgress=100;updateStatus="Aggiornamento scaricato. Pronto da installare.";}
        updateInput();
    }

    private GitHubUpdater createUpdater(){return new GitHubUpdater(activity,new GitHubUpdater.Listener(){
        @Override public void onChecking(){updateStatus="Controllo degli aggiornamenti…";invalidate();}
        @Override public void onUpToDate(String version){manualUpdateCheck=false;availableUpdate=null;updateReady=false;downloadProgress=-1;updateStatus="Hai già la versione più recente.";invalidate();tell("Topperia è aggiornato alla versione "+version+".");}
        @Override public void onUpdateAvailable(UpdateInfo info){boolean manual=manualUpdateCheck;manualUpdateCheck=false;UpdateInfo cached=updater.downloadedUpdate();availableUpdate=info;updateReady=cached!=null&&cached.sha256.equals(info.sha256);downloadProgress=updateReady?100:-1;updateStatus=updateReady?"Aggiornamento scaricato. Pronto da installare.":"Disponibile la versione "+info.versionName+".";invalidate();if(manual||(!screen.equals("game")&&!screen.equals("onboarding")&&!screen.equals("shuffle"))){if(updateReady)offerInstall(info);else offerUpdate(info);}}
        @Override public void onDownloadProgress(int progress){downloadProgress=progress;updateStatus=progress<0?"Scaricamento in corso…":"Scaricamento in corso · "+progress+"%";invalidate();}
        @Override public void onReadyToInstall(UpdateInfo info){availableUpdate=info;updateReady=true;downloadProgress=100;updateStatus="Aggiornamento verificato. Pronto da installare.";invalidate();offerInstall(info);}
        @Override public void onInstallPermissionRequired(){updatePrompts.post(()->updatePrompts.show(new AlertDialog.Builder(activity).setTitle("Consenti gli aggiornamenti").setMessage("Android richiede il permesso di installare aggiornamenti da Topperia. Apri l’impostazione e abilita il consenso: poi potrai confermare l’installazione.").setNegativeButton("Più tardi",(d,w)->updater.cancelPendingInstall()).setPositiveButton("Apri impostazione",(d,w)->updater.requestInstallPermission())));}
        @Override public void onInstallerOpened(){updateStatus="Conferma l’aggiornamento nella schermata Android.";invalidate();}
        @Override public void onError(String message){manualUpdateCheck=false;downloadProgress=-1;UpdateInfo cached=updater.downloadedUpdate();updateReady=cached!=null&&availableUpdate!=null&&cached.sha256.equals(availableUpdate.sha256);updateStatus=message;invalidate();updatePrompts.post(()->updatePrompts.show(new AlertDialog.Builder(activity).setTitle("Aggiornamento non completato").setMessage(message).setPositiveButton("Ho capito",null)));}
    });}
    private void offerUpdate(UpdateInfo info){
        updatePrompts.post(()->{
        String notes=info.notes==null?"":info.notes.replaceAll("(?m)^#{1,6}\\s*","").replace("**","").trim();if(notes.length()>1000)notes=notes.substring(0,1000)+"…";
        String size=String.format(Locale.ITALIAN,"%.1f MB",info.sizeBytes/1048576f);
        updatePrompts.show(new AlertDialog.Builder(activity).setTitle("Topperia "+info.versionName+" è pronta").setMessage("Puoi scaricare l’aggiornamento da GitHub ("+size+"). I tuoi salvataggi restano al loro posto."+(notes.isEmpty()?"":"\n\n"+notes)).setNegativeButton("Più tardi",null).setPositiveButton("Scarica",(d,w)->{downloadProgress=0;updateStatus="Preparazione del download…";invalidate();updater.beginDownload(info);}));
        });
    }
    private void offerInstall(UpdateInfo info){updatePrompts.post(()->updatePrompts.show(new AlertDialog.Builder(activity).setTitle("Tutto pronto per aggiornare").setMessage("Topperia "+info.versionName+" è stata scaricata e verificata. Android ti chiederà conferma per installarla.").setNegativeButton("Più tardi",null).setPositiveButton("Installa",(d,w)->updater.installDownloaded())));}
    private void updateAction(){if(updater.isBusy())return;if(updateReady){offerInstall(availableUpdate);return;}if(availableUpdate!=null){offerUpdate(availableUpdate);return;}manualUpdateCheck=true;updater.check(true);}
    public void resume(){readMotionPreference();invalidate();updatePrompts.onResume();updater.onResume();updater.check(false);}
    public void pause(){updatePrompts.onPause();stopScroll();clearPress();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);updatePrompts.onWindowFocusChanged(focused);}

    private float dp(float x){return x*getResources().getDisplayMetrics().density;}
    private boolean reduced(){return frameReduced;}
    private void readMotionPreference(){frameReduced=store.option("reduced",false)||!android.animation.ValueAnimator.areAnimatorsEnabled();}
    private long now(){return SystemClock.uptimeMillis();}
    private float anim(){return reduced()?0:(frameTime%100000)/1000f;}
    private boolean dockPage(String page){return page.equals("home")||page.equals("library")||page.equals("profile");}
    private int pageIndex(String page){return page.equals("home")?0:page.equals("library")?1:2;}
    private void capturePage(){
        if(capturing)return;
        if(getWidth()==0||reduced()||((screen.equals("game")||screen.equals("results"))&&session==null)) {previousPage=null;return;}
        Picture picture=new Picture();Canvas c=picture.beginRecording(400,(int)Math.ceil(H));c.drawColor(BG);
        capturing=true;
        try{
            drawPageFrame(c);
            c.save();c.scale(1/scale,1/scale);super.dispatchDraw(c);c.restore();
            if(!modal.isEmpty())modal(c);
        }finally{picture.endRecording();capturing=false;hitOffset=0;contentClip=false;}
        previousPage=picture;
    }
    private void show(String page){
        readMotionPreference();frameTime=now();capturePage();stopScroll();clearPress();
        pageDirection=dockPage(screen)&&dockPage(page)?Math.signum(pageIndex(page)-pageIndex(screen)):page.equals("home")?-1:1;
        if(pageDirection==0)pageDirection=1;
        if(dockPage(page)){navFrom=navPosition();navTarget=pageIndex(page);navAt=now();}
        screen=page;scroll=0;maxScroll=0;entered=pageAt=now();modal="";sheetClosing=false;sheetDrag=0;pageShift=0;
        hits.clear();accessibilityFocus=-1;hovered=-1;hideKeyboard();updateInput();requestLayout();invalidate();
        activity.setTitle("Topperia · "+(page.equals("home")?"Home":page.equals("library")?"Classifiche":page.equals("profile")?"Il tuo percorso":page.equals("results")?"Risultato":page.equals("onboarding")?"Benvenuto":page.equals("shuffle")?"Una sorpresa":"La tua scalata"));
        if(android.os.Build.VERSION.SDK_INT>=28)setAccessibilityPaneTitle(activity.getTitle());sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }
    private float navPosition(){float t=reduced()?1:Math.min(1,(now()-navAt)/380f);return navFrom+(navTarget-navFrom)*(1-(float)Math.pow(1-t,3));}
    private void updateInput(){boolean visible=screen.equals("game")&&session!=null&&session.phase()==GameSession.Phase.PLAYING&&modal.isEmpty();input.setVisibility(visible?VISIBLE:GONE);submit.setVisibility(visible?VISIBLE:GONE);}
    private void hideKeyboard(){input.clearFocus();((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(getWindowToken(),0);}
    private void feedback(boolean good){if(store.option("haptic",true)){int effect=android.os.Build.VERSION.SDK_INT>=30?(good?HapticFeedbackConstants.CONFIRM:HapticFeedbackConstants.REJECT):(good?HapticFeedbackConstants.KEYBOARD_TAP:HapticFeedbackConstants.LONG_PRESS);performHapticFeedback(effect);}if(store.option("sound",true)){try{if(tones==null)tones=new ToneGenerator(AudioManager.STREAM_MUSIC,35);tones.startTone(good?ToneGenerator.TONE_PROP_ACK:ToneGenerator.TONE_PROP_NACK,good?120:90);}catch(RuntimeException ignored){}}}
    private void tell(String text){notice=text;noticeAt=now();invalidate();announceForAccessibility(text);}

    private void start(RankedList list){
        if(session!=null&&session.phase()!=GameSession.Phase.FINISHED){new AlertDialog.Builder(activity).setTitle("Una nuova scalata?").setMessage("Iniziando questa Top 10 sostituirai la partita salvata.").setNegativeButton("Annulla",null).setPositiveButton("Inizia",(d,w)->{session=null;start(list);}).show();return;}
        session=new GameSession(list,pendingMode,pendingMode==GameSession.Mode.SOLO?"Tu":store.prefs.getString("player1","Giocatore 1"),store.prefs.getString("player2","Giocatore 2"));store.prefs.edit().putString("last_ranking",list.id).apply();recorded=false;finishedAt=0;lastHit=-1;Arrays.fill(reveals,0);input.setText("");show("game");save();
    }
    private void randomRound(String category){
        if(session!=null&&session.phase()!=GameSession.Phase.FINISHED){new AlertDialog.Builder(activity).setTitle("Una nuova sorpresa?").setMessage("La classifica casuale sostituirà la partita salvata.").setNegativeButton("Annulla",null).setPositiveButton("Sorprendimi",(d,w)->{session=null;save();randomRound(category);}).show();return;}
        ArrayList<RankedList> pool=new ArrayList<>();Set<String> played=new HashSet<>();for(RankedList r:rankings){if(category.equals("Tutte")||r.category.equals(category))pool.add(r);if(store.option("played_"+r.id,false))played.add(r.id);}
        if(pool.isEmpty()){tell("In questa categoria non ci sono ancora classifiche.");return;}
        queuedRanking=picker.pick(pool,played,store.prefs.getString("last_ranking",""));shuffleReturn=screen;show("shuffle");int generation=++shuffleGeneration;
        postDelayed(()->{if(screen.equals("shuffle")&&generation==shuffleGeneration&&queuedRanking!=null){RankedList selected=queuedRanking;queuedRanking=null;start(selected);}},reduced()?250:1650);
    }
    private void openOnboarding(){onboardingReturn=screen;onboardingReplay=true;onboardingStep=0;show("onboarding");}
    private void finishOnboarding(){store.prefs.edit().putBoolean("onboarding_complete",true).apply();show(onboardingReplay?onboardingReturn:"home");onboardingReplay=false;}
    private void onboardingPage(int step){if(step>=3){finishOnboarding();return;}readMotionPreference();capturePage();pageDirection=step>onboardingStep?1:-1;onboardingStep=Math.max(0,step);entered=pageAt=now();clearPress();accessibilityFocus=-1;hovered=-1;invalidate();sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
    private void choose(GameSession.Mode mode){pendingMode=mode;filter="Tutte";show("library");}
    private RankedList daily(){long day=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Rome")).toEpochDay();return rankings.get((int)Math.floorMod(day,rankings.size()));}
    private void guess(){
        if(session==null||!screen.equals("game"))return;
        if(now()-lastSubmissionAt<220)return;
        String answer=input.getText().toString(); GameSession.GuessResult result=session.submit(answer);
        switch(result.status){
            case EMPTY:tell("Scrivi una risposta per iniziare.");return;
            case DUPLICATE:tell("Già provata. Conservi il tentativo!");input.selectAll();return;
            case INACTIVE:return;
            case HIT:reveals[result.entryIndex]=now();lastHit=result.entryIndex;feedback(true);tell("Dentro! #"+position(result.entryIndex)+" · "+session.ranking.entries.get(result.entryIndex).name);break;
            case MISS:lastHit=-1;feedback(false);tell("Non è nella Top 10. Riprova!");break;
        }
        input.setText("");
        lastSubmissionAt=now();
        if(session.phase()!=GameSession.Phase.PLAYING){hideKeyboard();finishedAt=now();if(session.phase()==GameSession.Phase.FINISHED){record();}else{notice="";}updateInput();requestLayout();}
        save();invalidate();
    }
    private void record(){if(!recorded){store.record(session);recorded=true;}}
    private String position(int index){return session.ranking.id.startsWith("skyscrapers")&&(index==7||index==8)?"8=":""+(index+1);}
    public void save(){android.content.SharedPreferences.Editor edit=store.prefs.edit();if(session!=null){edit.putString("session",session.serialize()).putString("ranking",session.ranking.id).putBoolean("recorded",recorded);}else{edit.remove("session").remove("ranking");}edit.apply();}
    private void restore(){String id=store.prefs.getString("ranking","");for(RankedList list:rankings)if(list.id.equals(id)){try{session=GameSession.restore(list,store.prefs.getString("session",""));recorded=store.prefs.getBoolean("recorded",false);}catch(RuntimeException ignored){session=null;}break;}}
    public void dispose(){updatePrompts.close();stopScroll();previousPage=null;shuffleGeneration++;queuedRanking=null;updater.close();if(tones!=null)tones.release();}
    public void goBack(){if(!modal.isEmpty()){closeSheet();}else if(screen.equals("onboarding")){if(onboardingStep>0)onboardingPage(onboardingStep-1);else if(onboardingReplay)finishOnboarding();else activity.finish();}else if(screen.equals("shuffle")){shuffleGeneration++;queuedRanking=null;show(shuffleReturn);}else if(screen.equals("game")){confirmExit();}else if(!screen.equals("home")){show("home");}else activity.finish();}
    private void confirmExit(){hideKeyboard();new AlertDialog.Builder(activity).setTitle("Una pausa dalla vetta?").setMessage("La partita viene salvata. Puoi riprenderla dalla home quando vuoi.").setNegativeButton("Continua",(d,w)->{}).setPositiveButton("Torna alla home",(d,w)->{save();show("home");}).show();}
    private void source(){RankedList r=session!=null?session.ranking:daily();new AlertDialog.Builder(activity).setTitle("Dati veri, curiosità vere.").setMessage(r.sourceName+" · "+r.referenceYear+"\n\n"+r.methodology+"\n\n"+r.sourceUrl).setNegativeButton("Chiudi",null).setPositiveButton("Apri la fonte",(d,w)->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(r.sourceUrl)));}catch(Exception e){tell("Nessun browser disponibile.");}}).show();}
    private void editNames(){LinearLayout fields=new LinearLayout(activity);fields.setOrientation(LinearLayout.VERTICAL);int pad=(int)dp(22);fields.setPadding(pad,pad,pad,0);EditText first=new EditText(activity),second=new EditText(activity);first.setSingleLine();second.setSingleLine();first.setHint("Giocatore o squadra 1");second.setHint("Giocatore o squadra 2");first.setText(store.prefs.getString("player1","Giocatore 1"));second.setText(store.prefs.getString("player2","Giocatore 2"));first.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});second.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});fields.addView(first);fields.addView(second);new AlertDialog.Builder(activity).setTitle("Chi sfida chi?").setView(fields).setNegativeButton("Annulla",null).setPositiveButton("Pronti!",(d,w)->{String a=first.getText().toString().trim(),b=second.getText().toString().trim();store.prefs.edit().putString("player1",a.isEmpty()?"Giocatore 1":a).putString("player2",b.isEmpty()?"Giocatore 2":b).apply();invalidate();}).show();}

    @Override protected void onMeasure(int w,int h){int ww=MeasureSpec.getSize(w),hh=MeasureSpec.getSize(h);setMeasuredDimension(ww,hh);scale=ww/400f;H=hh/scale;keyboard=H<620;input.measure(MeasureSpec.makeMeasureSpec((int)(258*scale),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((int)(52*scale),MeasureSpec.EXACTLY));submit.measure(MeasureSpec.makeMeasureSpec((int)(52*scale),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((int)(52*scale),MeasureSpec.EXACTLY));}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){float y=H-82;inputY=y;input.layout((int)(38*scale),(int)((y+7)*scale),(int)(296*scale),(int)((y+59)*scale));submit.layout((int)(318*scale),(int)((y+7)*scale),(int)(370*scale),(int)((y+59)*scale));}

    @Override protected void onDraw(Canvas raw){
        super.onDraw(raw);frameTime=now();readMotionPreference();frameAnimating=false;
        if(reduced()){previousPage=null;if(!scroller.isFinished())scroller.abortAnimation();}
        if(!modal.isEmpty()&&sheetClosing&&sheetProgress()<=0){modal="";sheetClosing=false;sheetDrag=0;updateInput();sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
        Canvas c=raw;c.save();c.scale(scale,scale);c.drawColor(BG);hits.clear();hitOffset=0;contentClip=false;
        String summary=screen.equals("home")?"Topperia. Il mondo è una Top 10. Scegli una sfida.":screen.equals("library")?"Scegli una classifica. Modalità "+(pendingMode==GameSession.Mode.DUEL?"due giocatori":"solitaria"):screen.equals("profile")?"Il tuo percorso. Record "+store.number("best")+" su 10. "+store.number("rounds")+" partite.":screen.equals("onboarding")?"Benvenuto in Topperia. Introduzione, pagina "+(onboardingStep+1)+" di 3.":screen.equals("shuffle")?"Scelgo una classifica casuale per te.":"Topperia";
        if(!summary.contentEquals(getContentDescription()==null?"":getContentDescription()))setContentDescription(summary);
        drawPageFrame(c);
        float nativeProgress=previousPage==null?1:Motion.ease(Motion.progress(now(),pageAt,340));
        input.setTranslationX(pageShift*scale);submit.setTranslationX(pageShift*scale);
        input.setAlpha(nativeProgress);submit.setAlpha(nativeProgress);input.setEnabled(nativeProgress>.8f);submit.setEnabled(nativeProgress>.8f);
        if(dockPage(screen))nav(c,screen);
        if(!modal.isEmpty())modal(c);
        if(!notice.isEmpty()&&now()-noticeAt<2900&&modal.isEmpty()){
            float y=screen.equals("game")?Math.max(208,H-124):H-112;
            float age=now()-noticeAt,t=reduced()?1:Math.min(Motion.ease(age/220f),Motion.ease((2900-age)/240f));
            c.save();c.translate(0,(1-t)*12);c.saveLayerAlpha(22,y,378,y+42,(int)(255*t));
            rect(c,22,y,356,42,15,INK);center(c,notice,200,y+26,12,WHITE,bold);c.restore();c.restore();if(reduced())postInvalidateDelayed((long)(2900-age)+1);else frameAnimating=true;
        }
        drawPress(c);
        c.restore();
        if(isShown()&&getWindowVisibility()==VISIBLE&&frameAnimating)postInvalidateOnAnimation();
    }

    // The incoming page stays opaque. Only the retained outgoing content fades,
    // so a navigation frame can never expose an empty background or blink the dock.
    private void drawPageFrame(Canvas c){
        float t=reduced()?1:Motion.ease(Motion.progress(now(),pageAt,340));
        pageShift=previousPage==null?0:pageDirection*18*(1-t);
        c.save();if(dockPage(screen))c.clipRect(0,0,400,H-DOCK_SPACE);
        c.save();c.translate(pageShift,0);drawPage(c);c.restore();
        if(previousPage!=null&&t<1){
            int layer=c.saveLayerAlpha(0,0,400,H,(int)(255*(1-t)));
            c.translate(-pageDirection*10*t,0);c.drawPicture(previousPage);c.restoreToCount(layer);frameAnimating=true;
        }else if(!capturing)previousPage=null;
        c.restore();
    }
    private void drawPage(Canvas c){switch(screen){case "onboarding":onboarding(c);break;case "shuffle":shuffle(c);break;case "home":home(c);break;case "library":library(c);break;case "profile":profile(c);break;case "game":game(c);break;case "results":results(c);break;}}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);previousPage=null;stopScroll();}
    @Override protected void onDetachedFromWindow(){stopScroll();previousPage=null;super.onDetachedFromWindow();}
    @Override public void computeScroll(){if(scroller.computeScrollOffset()){scroll=Math.max(0,Math.min(maxScroll,scroller.getCurrY()/scale));postInvalidateOnAnimation();}}

    private void stopScroll(){scroller.abortAnimation();if(velocity!=null){velocity.recycle();velocity=null;}activePointer=-1;scrollingGesture=false;}
    private float sheetProgress(){return reduced()?sheetTarget:Motion.lerp(sheetFrom,sheetTarget,Motion.ease(Motion.progress(now(),sheetAt,sheetClosing?240:360)));}
    private float sheetHeight(){return modal.equals("settings")?565:482;}
    private float sheetTop(){return Math.max(20,H-sheetHeight())+(1-sheetProgress())*sheetHeight()+sheetDrag;}
    private void openSheet(String name){readMotionPreference();stopScroll();clearPress();modal=name;sheetFrom=0;sheetTarget=1;sheetAt=now();sheetDrag=0;sheetClosing=false;hits.clear();accessibilityFocus=hovered=-1;hideKeyboard();updateInput();invalidate();sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
    private void closeSheet(){if(modal.isEmpty()||sheetClosing)return;sheetFrom=Math.max(0,sheetProgress()-sheetDrag/sheetHeight());sheetTarget=0;sheetAt=now();sheetClosing=true;sheetDrag=0;clearPress();invalidate();}
    private void clearPress(){pressedBounds=null;pressRelease=1;}
    private void releasePress(){if(pressedBounds!=null){pressAt=now();pressRelease=0;invalidate();}}
    private void drawPress(Canvas c){
        if(pressedBounds==null||reduced())return;
        float t=pressRelease==0?Motion.progress(now(),pressAt,220):0;
        if(pressRelease==0&&t>=1){clearPress();return;}
        float strength=pressRelease==0?1-Motion.ease(t):Motion.ease(Motion.progress(now(),pressAt,100));
        RectF b=pressedBounds;stroke(c,b.left+2,b.top+2,b.width()-4,b.height()-4,18,(Math.round(48*strength)<<24)|(INK&0xffffff),2);
        if(pressRelease==0||now()-pressAt<100)frameAnimating=true;
    }

    private void pageStart(Canvas c){c.save();c.clipRect(0,0,400,H-DOCK_SPACE);c.translate(0,-scroll);hitOffset=-scroll;contentClip=true;}
    private void pageEnd(Canvas c,float height){c.restore();hitOffset=0;contentClip=false;maxScroll=Math.max(0,height-(H-DOCK_SPACE));scroll=Math.min(scroll,maxScroll);}
    private void header(Canvas c){brand(c,25,38,1);circle(c,353,31,21,WHITE);icon(c,"settings",343,21,20,INK);hit(328,7,50,49,"Impostazioni",()->openSheet("settings"));}

    private void onboarding(Canvas c){
        if(!reduced())frameAnimating=true;
        maxScroll=0;brand(c,25,40,.91f);pill(c,onboardingReplay?"Chiudi":"Salta",311,14,65,36,0xffeeefe6,MUTED,12);hit(305,9,76,47,"Salta introduzione",this::finishOnboarding);
        for(int i=0;i<3;i++)rect(c,25+i*49,74,40,4,2,i<=onboardingStep?GREEN:0xffdfe5d5);
        text(c,"0"+(onboardingStep+1)+" / 03",328,80,10,MUTED,bold);
        float artHeight=Math.min(300,Math.max(170,H*.34f)),artY=104+artHeight/2;
        float contentScale=artHeight/300;
        c.save();c.translate(200,artY);c.scale(contentScale,contentScale);
        float sway=(float)Math.sin(anim()*1.5f)*5;
        if(onboardingStep==0){
            circle(c,0,5,137,0xffedf1e2);oval(c,-116,107,232,21,0x15183b32);
            c.save();c.translate(0,sway-2);c.rotate((float)Math.sin(anim()*.7f)*3);logoMark(c,-152,-152,304);c.restore();
            pill(c,"10 POSTI. UN MONDO INTERO.",-112,127,224,29,WHITE,GREEN,9);
            star(c,105,-109,10,GREEN);star(c,-109,76,7,ORANGE);
        }else if(onboardingStep==1){
            circle(c,0,0,137,LILAC);
            for(int i=0;i<3;i++){c.save();c.rotate((i-1)*7);rect(c,-105,-95+i*11,210,93,20,i==2?WHITE:0xffc9bae8);c.restore();}
            float cycle=reduced()?3.4f:(now()-entered)%5200/1000f;
            boolean solved=cycle>1.75f;float turn=reduced()?1:Math.min(1,Math.max(0,(cycle-1.65f)/.45f));
            float stretch=turn>0&&turn<1?Math.max(.05f,Math.abs((float)Math.cos(turn*Math.PI))):1;
            c.save();c.scale(stretch,1);rect(c,-111,-77,222,104,21,solved?LIME:WHITE);circle(c,-79,-27,18,solved?GREEN:0xffe9ede1);center(c,solved?"1":"?",-79,-21,18,solved?WHITE:MUTED,bold);
            text(c,solved?"Repubblica Ceca":"Chi c’è lassù?",-49,-30,16,INK,bold);text(c,solved?"148,8 litri per abitante":"Una casella da scoprire",-49,-7,10,MUTED,regular);c.restore();
            c.save();c.translate(0,sway);rect(c,-127,64,254,60,20,WHITE);icon(c,"spark",-111,82,22,GREEN);String written=cycle<.5f?"La tua idea…":cycle<1?"Ce":cycle<1.4f?"Cech": "Cechia";text(c,written,-77,101,19,INK,bold);circle(c,99,94,20,solved?GREEN:LIME);center(c,solved?"✓":"↑",99,101,21,solved?WHITE:INK,bold);c.restore();
            if(solved){star(c,124,-81,10,GREEN);star(c,-126,-53,6,ORANGE);pill(c,"DENTRO!",-47,-121,94,26,GREEN,WHITE,10);}
        }else{
            circle(c,0,4,137,0xffedf1e2);
            line(c,-76,4,76,4,0xffccd9b6,2);
            c.save();c.translate(-72,-13+sway);c.rotate(-8);iconBadge(c,"solo",-48,-48,96,LIME);pill(c,"TU",-30,62,60,25,WHITE,INK,10);c.restore();
            c.save();c.translate(73,17-sway);c.rotate(8);iconBadge(c,"duel",-48,-48,96,LILAC);pill(c,"IN DUE",-39,62,78,25,WHITE,INK,10);c.restore();
            circle(c,0,2,25,GREEN);center(c,"vs",0,8,18,WHITE,heavy);
            star(c,-14,-110,11,ORANGE);star(c,122,85,8,GREEN);star(c,-123,72,6,LILAC);
            c.save();c.translate(-7,115);c.rotate((float)Math.sin(anim())*8);dice(c,-22,-22,44,GREEN,LIME,5);c.restore();
        }
        c.restore();
        float textY=104+artHeight+44;
        String[] first={"Il mondo ha","Un’idea. Un posto","La curiosità"},second={"dieci posti.","in classifica.","si gioca in due."};
        text(c,first[onboardingStep],25,textY,33,INK,heavy);text(c,second[onboardingStep],25,textY+39,33,INK,heavy);
        String[] detail={"Ogni Top 10 nasconde risposte reali. A te il piacere di scoprirle.","Hai 10 tentativi. Scrivi una risposta: se c’è, trova subito il suo posto.","Da solo o sullo stesso telefono: chi scopre più risposte vince la sfida."};
        wrap(c,detail[onboardingStep],26,textY+76,345,15,22,MUTED,regular,3);
        read(24,textY-30,352,160,first[onboardingStep]+" "+second[onboardingStep]+" "+detail[onboardingStep]);
        if(H-textY>258){String[] notes={"Dati veri, curiosità vere. E nessuna fretta.","Ripetizioni gratuite. Ogni scoperta vale un punto.","Non sai cosa scegliere? C’è sempre una sorpresa."};icon(c,onboardingStep==2?"dice":"spark",26,textY+149,17,GREEN);fit(c,notes[onboardingStep],52,textY+162,325,11,GREEN,regular);}
        float footerY=H-86;
        if(onboardingStep>0){circle(c,49,footerY+27,25,0xffe9ede2);icon(c,"back",39,footerY+17,20,INK);hit(24,footerY+2,50,50,"Pagina precedente",()->onboardingPage(onboardingStep-1));}
        button(c,onboardingStep==2?"Esplora Topperia  →":"Continua  →",onboardingStep>0?88:24,footerY,onboardingStep>0?288:352,55,INK,WHITE,()->onboardingPage(onboardingStep+1));
        center(c,onboardingStep==2?"LA PROSSIMA SCOPERTA È TUA":"Scorri per continuare",200,H-14,9,MUTED,onboardingStep==2?bold:regular);
    }

    private void dice(Canvas c,float x,float y,float size,int bg,int fg,int face){
        rect(c,x,y+size*.06f,size,size,size*.25f,0x18183b32);rect(c,x,y,size,size,size*.25f,bg);
        float r=size*.065f,a=size*.29f,b=size*.71f,m=size*.5f;
        if(face%2==1)circle(c,x+m,y+m,r,fg);
        if(face>=2){circle(c,x+a,y+a,r,fg);circle(c,x+b,y+b,r,fg);}
        if(face>=4){circle(c,x+a,y+b,r,fg);circle(c,x+b,y+a,r,fg);}
        if(face==6){circle(c,x+a,y+m,r,fg);circle(c,x+b,y+m,r,fg);}
    }

    private void shuffle(Canvas c){
        if(!reduced())frameAnimating=true;
        maxScroll=0;circle(c,43,30,20,WHITE);icon(c,"back",33,20,20,INK);hit(20,7,46,48,"Annulla scelta casuale",this::goBack);brand(c,147,37,.8f);
        float cy=H*.42f,t=reduced()?1:Math.min(1,(now()-entered)/1650f);
        circle(c,200,cy,112,0xffedf1e2);
        for(int i=0;i<7;i++){double angle=(i*2*Math.PI/7)+anim()*.75;float radius=126+(float)Math.sin(anim()*2+i)*6;float x=200+(float)Math.cos(angle)*radius,y=cy+(float)Math.sin(angle)*radius;circle(c,x,y,i%2==0?5:3,new int[]{LILAC,ORANGE,LIME}[i%3]);}
        c.save();c.translate(200,cy);c.rotate(reduced()?-8:(1-t)*(float)Math.sin(t*19)*24);float z=1+(reduced()?0:(float)Math.sin(t*Math.PI)*.16f);c.scale(z,z);dice(c,-49,-49,98,GREEN,LIME,reduced()?5:1+(int)((now()-entered)/105)%6);c.restore();
        center(c,"La curiosità decide.",200,cy+182,28,INK,heavy);center(c,"Una Top 10 sta per trovarti.",200,cy+214,14,MUTED,regular);
        for(int i=0;i<3;i++)circle(c,184+i*16,cy+249,3.5f,reduced()||((now()-entered)/220)%3==i?GREEN:0xffdce4d0);
    }

    private void home(Canvas c){
        pageStart(c);header(c);
        text(c,"POCHE REGOLE. GRANDI SCOPERTE.",25,87,10,MUTED,bold);
        text(c,"Il mondo è una",23,130,37,INK,heavy);text(c,"Top 10.",23,172,42,INK,heavy);
        text(c,"Quanto in alto riesci ad arrivare?",25,201,15,MUTED,regular);
        hero(c,24,224,352,196);
        float y=444;
        text(c,"Scegli la tua sfida",25,y,20,INK,bold);pill(c,"COME SI GIOCA",267,y-19,108,24,0x00000000,MUTED,9);hit(260,y-25,119,36,"Come si gioca",()->openSheet("rules"));
        y+=18;
        actionCard(c,24,y,352,83,LIME,"solo","In solitaria","Tu, 10 tentativi e un po’ d’intuito.",()->choose(GameSession.Mode.SOLO));
        y+=95;
        actionCard(c,24,y,352,83,LILAC,"duel","Sfida 1 contro 1","Stesso telefono. Doppia curiosità.",()->choose(GameSession.Mode.DUEL));
        y+=109;
        if(session!=null){
            boolean completed=session.phase()==GameSession.Phase.FINISHED;
            rect(c,24,y-4,352,65,19,WHITE);icon(c,"play",40,y+14,24,GREEN);text(c,completed?"La tua ultima scalata":"Una vetta ti aspetta",77,y+21,15,INK,bold);text(c,completed?"Riguarda la classifica e il risultato":"Riprendi la partita salvata",77,y+40,12,MUTED,regular);chevron(c,351,y+25);hit(24,y-4,352,65,completed?"Riguarda ultimo risultato":"Riprendi partita",()->{show(completed?"results":"game");});y+=80;
        }
        text(c,"LA CURIOSITÀ DEL GIORNO",25,y,10,MUTED,bold);y+=14;
        RankedList day=daily();rect(c,24,y,352,95,20,WHITE);iconBadge(c,day.icon,40,y+20,48,0xffeef0e5);wrap(c,day.title,103,y+28,248,16,21,INK,bold,2);text(c,day.category+"  ·  "+day.referenceYear,103,y+77,11,MUTED,regular);hit(24,y,352,95,"Gioca: "+day.title,()->{pendingMode=GameSession.Mode.SOLO;start(day);});
        y+=119;text(c,"Dati reali. Risposte sorprendenti. Zero fretta.",25,y,11,MUTED,regular);
        pageEnd(c,y+23);
    }

    private void hero(Canvas c,float x,float y,float w,float h){
        if(c.quickReject(x,y,x+w,y+h,Canvas.EdgeType.AA))return;
        rect(c,x,y+4,w,h,28,0xffe7ebdc);rect(c,x,y,w,h,28,GREEN);
        c.save();c.clipRect(x,y,x+w,y+h);
        for(int i=0;i<13;i++)for(int j=0;j<7;j++)circle(c,x+12+i*28,y+12+j*29,1,0x234f8059);
        pill(c,"LASCIA FARE ALLA CURIOSITÀ",x+17,y+18,200,23,0xff456e4e,0xffe6f5cd,8.3f);
        text(c,"Dieci posti.",x+20,y+77,25,WHITE,heavy);text(c,"Una sorpresa.",x+20,y+109,25,WHITE,heavy);
        rect(c,x+17,y+133,169,42,15,LIME);icon(c,"dice",x+29,y+145,18,INK);text(c,"Sorprendimi",x+57,y+159,14,INK,bold);
        c.save();c.translate(x+266,y+115);c.scale(.85f,.85f);podium(c,0,0);c.restore();
        c.restore();
        hit(x,y,w,h,"Sorprendimi: gioca una classifica casuale in solitaria",()->{pendingMode=GameSession.Mode.SOLO;randomRound("Tutte");});
    }

    private void library(Canvas c){
        pageStart(c);header(c);text(c,pendingMode==GameSession.Mode.DUEL?"MEGLIO IN DUE":"ALLENA LA CURIOSITÀ",25,88,10,MUTED,bold);
        text(c,"La prossima Top 10?",23,132,31,INK,heavy);
        if(pendingMode==GameSession.Mode.DUEL){fit(c,store.prefs.getString("player1","Giocatore 1")+" vs "+store.prefs.getString("player2","Giocatore 2")+"  ✎",25,162,350,14,GREEN,bold);hit(24,140,352,35,"Personalizza giocatori o squadre",this::editNames);}else text(c,"Scegli un mondo. Mettiti alla prova.",25,162,14,MUTED,regular);
        float cy=183;
        float cx=24;for(String category:categories){float w=measure(category,11,bold)+26;if(cx+w>379){cx=24;cy+=38;}pill(c,category,cx,cy,w,30,filter.equals(category)?INK:0xffeeefe7,filter.equals(category)?WHITE:MUTED,11);final String choice=category;hit(cx,cy,w,30,"Categoria "+category,()->{if(!filter.equals(choice)){stopScroll();filter=choice;scroll=0;filterAt=now();accessibilityFocus=hovered=-1;invalidate();sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}});cx+=w+7;}
        float y=cy+46;
        rect(c,24,y,352,77,21,LIME);text(c,"Sorprendimi",42,y+30,19,INK,heavy);text(c,filter.equals("Tutte")?"Una Top 10 a caso. Il resto lo fai tu.":"Una sorpresa nella categoria "+filter+".",42,y+53,11,INK,regular);
        if(!c.quickReject(310,y,354,y+77,Canvas.EdgeType.AA)){if(!reduced())frameAnimating=true;c.save();c.translate(332,y+38);c.rotate((float)Math.sin(anim()*1.4f)*7);dice(c,-22,-22,44,INK,LIME,5);c.restore();}
        hit(24,y,352,77,"Classifica casuale: "+filter,()->randomRound(filter));y+=103;
        int visibleCount=0;for(RankedList r:rankings)if(filter.equals("Tutte")||r.category.equals(filter))visibleCount++;
        text(c,String.format(Locale.ITALIAN,"%02d CLASSIFICHE · 10 TENTATIVI CIASCUNA",visibleCount),25,y-5,9.5f,MUTED,bold);y+=12;
        int drawn=0;for(int i=0;i<rankings.size();i++){
            RankedList r=rankings.get(i);if(!filter.equals("Tutte")&&!filter.equals(r.category))continue;
            // Clipping alone still measures every title and constructs every icon.
            // Skip all row work until its bounds actually enter the viewport.
            if(y+139<scroll||y-scroll>H-DOCK_SPACE){y+=140;continue;}
            float offset=reduced()?0:12*(1-Motion.ease(Motion.progress(now(),Math.max(filterAt,pageAt)+Math.min(drawn++,4)*28,320)));
            if(offset>0)frameAnimating=true;c.save();c.translate(0,offset);hitOffset+=offset;
            pressTransform(c,24,y,352,127);
            int accent=i%4==0?LIME:i%4==1?LILAC:i%4==2?ORANGE:0xffc8e5da;
            rect(c,24,y,352,127,21,WHITE);iconBadge(c,r.icon,40,y+17,43,accent);
            text(c,r.category.toUpperCase(Locale.ITALIAN),97,y+29,9,MUTED,bold);if(store.option("played_"+r.id,false)){text(c,"✓ GIOCATA",293,y+29,8,GREEN,bold);}
            wrap(c,r.title,97,y+54,254,17,22,INK,bold,2);
            fit(c,r.referenceYear+"  ·  "+r.sourceName,41,y+108,292,10,MUTED,regular);chevron(c,350,y+105);
            c.restore();hit(24,y,352,127,"Gioca: "+r.title,()->start(r));c.restore();hitOffset-=offset;y+=140;
        }
        text(c,"Ogni classifica racconta dati di un anno preciso.",25,y+8,10,MUTED,regular);pageEnd(c,y+36);
    }

    private void profile(Canvas c){
        pageStart(c);header(c);text(c,"LA TUA PICCOLA SCALATA",25,89,10,MUTED,bold);text(c,"Ciao, mente curiosa.",23,132,31,INK,heavy);
        rect(c,24,159,352,167,25,LILAC);c.save();c.translate(295,220);c.scale(.8f,.8f);trophy(c,0,0,LIME);c.restore();
        text(c,"IL TUO RECORD",45,192,10,INK,bold);text(c,""+store.number("best"),42,259,65,INK,heavy);text(c,"/ 10",97+(store.number("best")==10?30:0),258,24,INK,bold);text(c,"C’è sempre una nuova vetta.",45,300,12,INK,regular);
        float y=346;stat(c,24,y,107,""+store.number("rounds"),"PARTITE");stat(c,146,y,107,""+store.number("hits"),"SCOPERTE");int tries=store.number("attempts");stat(c,268,y,108,(tries==0?"—":Math.round(store.number("hits")*100f/tries)+"%"),"PRECISIONE");
        y+=128;text(c,"Piccole grandi conquiste",25,y,20,INK,bold);y+=19;
        achievement(c,y,"La prima vetta","Completa la tua prima partita",store.number("rounds")>=1,"flag");y+=83;
        achievement(c,y,"A metà del cielo","Trova almeno 5 risposte in un round",store.number("best")>=5,"star");y+=83;
        achievement(c,y,"Cima perfetta","Scopri una Top 10 completa",store.number("best")==10,"trophy");y+=83;
        achievement(c,y,"Due menti, una sfida","Completa una partita 1 contro 1",store.number("duels")>=1,"duel");y+=105;
        pill(c,"COME SI GIOCA",25,y,157,40,0xffeeefe7,INK,11);hit(25,y,157,40,"Come si gioca",()->openSheet("rules"));
        pageEnd(c,y+64);
    }
    private void stat(Canvas c,float x,float y,float w,String number,String label){rect(c,x,y,w,98,20,WHITE);center(c,number,x+w/2,y+43,28,INK,heavy);center(c,label,x+w/2,y+72,9,MUTED,bold);}
    private void achievement(Canvas c,float y,String title,String detail,boolean unlocked,String symbol){rect(c,24,y,352,72,18,unlocked?0xffedf2dd:0xffeeefe8);iconBadge(c,symbol,38,y+14,43,unlocked?LIME:0xffe0e3d8);text(c,title,96,y+30,15,unlocked?INK:MUTED,bold);text(c,detail,96,y+50,10.5f,MUTED,regular);if(unlocked)text(c,"✓",347,y+39,17,GREEN,bold);}

    private void game(Canvas c){
        if(session==null)return;
        maxScroll=0;scroll=0;
        circle(c,43,30,20,WHITE);icon(c,"back",33,20,20,INK);hit(20,7,46,48,"Esci dalla partita",this::confirmExit);
        brand(c,147,37,.8f);circle(c,357,30,20,WHITE);icon(c,"info",347,20,20,INK);hit(333,7,47,48,"Fonte della classifica",this::source);
        RankedList r=session.ranking;float titleY=keyboard?75:89;
        text(c,r.category.toUpperCase(Locale.ITALIAN)+"  /  "+r.referenceYear,25,titleY,9,MUTED,bold);
        wrap(c,r.title,24,titleY+32,350,keyboard?23:28,keyboard?27:33,INK,heavy,2);
        if(!keyboard)fit(c,r.subtitle,25,184,350,11.5f,MUTED,regular);
        float progressY=keyboard?150:216;
        String who=session.mode==GameSession.Mode.DUEL?session.currentPlayer().name:"La tua scalata";
        text(c,who,25,progressY,12,INK,bold);text(c,session.currentPlayer().score()+" / 10 scoperte",257,progressY,12,GREEN,bold);
        int used=session.currentPlayer().attemptsUsed();for(int i=0;i<10;i++){float x=25+i*19;float pulse=!reduced()&&i==used-1?1+(float)Math.sin(Math.PI*Motion.progress(now(),lastSubmissionAt,380))*.6f:1;c.save();c.scale(1,pulse,x+6.5f,progressY+15.5f);rect(c,x,progressY+13,13,5,2.5f,i<used?GREEN:0xffdfe4d5);c.restore();}
        if(!reduced()&&now()-lastSubmissionAt<1000)frameAnimating=true;
        text(c,(10-used)+" tentativi",267,progressY+20,11,MUTED,regular);
        float boardY=progressY+38;
        read(24,64,352,progressY-59,r.title+". "+r.subtitle+". "+who+". "+session.currentPlayer().score()+" risposte trovate su 10. "+(10-used)+" tentativi rimasti.");
        float available=H-boardY-117;
        float tileH=Math.max(24,Math.min(64,(available-32)/5));
        for(int i=0;i<10;i++){
            float x=24+(i%2)*181,y=boardY+(i/2)*(tileH+8);
            boolean found=session.currentPlayer().isRevealed(i);
            boolean done=session.phase()==GameSession.Phase.FINISHED;
            float elapsed=(now()-finishedAt-i*65)/430f;
            boolean reveal=found||(done&&(reduced()||elapsed>0));
            int fill=found?LIME:(reveal?0xffeceee2:WHITE);
            float flip=1;
            if(!reduced()&&found&&reveals[i]>0){float t=Math.min(1,(now()-reveals[i])/450f);flip=Math.abs((float)Math.cos(t*Math.PI));if(t<.48f)fill=WHITE;if(t<1)frameAnimating=true;}
            if(!reduced()&&done&&elapsed<1)frameAnimating=true;
            c.save();c.translate(x+85.5f,y+tileH/2);c.scale(Math.max(.06f,flip),1);c.translate(-85.5f,-tileH/2);
            rect(c,0,2,171,tileH,16,0xffe7e9df);rect(c,0,0,171,tileH,16,fill);
            if(reveal){
                circle(c,20,tileH/2,12,found?GREEN:0xffdce1d1);center(c,position(i),20,tileH/2+4,11,found?WHITE:MUTED,bold);
                RankedList.Entry entry=r.entries.get(i);fit(c,entry.name,40,tileH/2+(tileH>44?-2:4),121,13,INK,bold);
                if(tileH>44)fit(c,entry.value,40,tileH/2+15,120,9.3f,MUTED,regular);
            }else{
                text(c,String.format(Locale.ITALIAN,"%02d",i+1),14,tileH/2+6,18,0xffb5beac,heavy);
                line(c,56,tileH/2,133,tileH/2,0xffe0e5d9,3);circle(c,147,tileH/2,3,0xffe0e5d9);
            }
            c.restore();
            if(!reduced()&&found&&i==lastHit){float burst=Motion.progress(now(),reveals[i],850);if(burst<1){frameAnimating=true;for(int j=0;j<7;j++){double angle=j*Math.PI*2/7;float distance=12+Motion.ease(burst)*30;circle(c,x+85.5f+(float)Math.cos(angle)*distance,y+tileH/2+(float)Math.sin(angle)*distance,2*(1-burst),j%2==0?GREEN:ORANGE);}}}
            read(x,y,171,tileH,reveal?"Posizione "+position(i)+". "+r.entries.get(i).name+". "+r.entries.get(i).value+(found?". Trovata.":". Svelata alla fine."):"Posizione "+(i+1)+". Ancora da scoprire.");
        }
        if(session.phase()==GameSession.Phase.PLAYING){
            float y=H-82;rect(c,24,y,352,66,22,WHITE);stroke(c,24,y,352,66,22,LINE,1);
            if(!keyboard)center(c,"Niente fretta. Fidati del tuo intuito.",200,H-95,10,MUTED,regular);
        }else if(session.phase()==GameSession.Phase.HANDOFF){
            handoff(c);
        }else{
            float y=H-78;button(c,"Guarda il risultato",24,y,352,53,INK,WHITE,()->show("results"));
        }
    }

    private void handoff(Canvas c){
        c.drawColor(BG);if(!capturing)hits.clear();circle(c,43,30,20,WHITE);icon(c,"back",33,20,20,INK);hit(20,7,46,48,"Metti in pausa la sfida",this::confirmExit);float cy=Math.max(115,H/2-135);
        iconBadge(c,"duel",161,cy,78,LILAC);center(c,"Cambio di mente!",200,cy+120,29,INK,heavy);
        center(c,"Passa il telefono a",200,cy+154,15,MUTED,regular);center(c,session.player(1).name,200,cy+185,24,INK,bold);
        center(c,"Stessa classifica. Dieci nuovi tentativi.",200,cy+225,13,MUTED,regular);
        center(c,"Le risposte si sveleranno alla fine.",200,cy+248,12,MUTED,regular);
        read(24,cy+86,352,177,"Cambio di mente. Passa il telefono a "+session.player(1).name+". Stessa classifica, dieci nuovi tentativi. Le risposte si sveleranno alla fine.");
        button(c,"Sono pronto, si gioca!",24,Math.min(H-90,cy+290),352,57,INK,WHITE,()->{capturePage();session.startSecondPlayer();pageAt=entered=now();Arrays.fill(reveals,0);finishedAt=0;input.setText("");updateInput();requestLayout();save();invalidate();});
    }

    private void results(Canvas c){
        if(session==null)return;
        pageStart(c);brand(c,25,38,1);pill(c,"PARTITA COMPLETATA",228,16,148,28,0xffeaf0dd,GREEN,9);
        c.save();c.translate(200,119);trophy(c,0,0,LIME);c.restore();
        if(!reduced()&&scroll<200){frameAnimating=true;for(int i=0;i<20;i++){float x=(i*83+13)%390,y=64+(i*47)%123+(float)Math.sin(anim()+i)*6; c.save();c.rotate(i*27+anim()*5,x,y);rect(c,x,y,4,8,1,i%3==0?LILAC:i%3==1?LIME:ORANGE);c.restore();}}
        boolean duel=session.mode==GameSession.Mode.DUEL;int score=session.player(0).score();
        String headline=duel?(session.winnerIndex()<0?"Una sfida alla pari!":session.player(session.winnerIndex()).name+" vince!"):(score==10?"Sei sulla vetta!":score>=7?"Che bella scalata!":score>=4?"L’intuito c’è!":"La curiosità vince sempre.");
        centerFit(c,headline,200,218,350,29,INK,heavy);
        read(24,194,352,135,headline+". "+session.player(0).name+": "+score+" risposte trovate su 10."+(duel?" "+session.player(1).name+": "+session.player(1).score()+" risposte trovate su 10.":""));
        if(duel){center(c,session.player(0).name+"   "+score+"  :  "+session.player(1).score()+"   "+session.player(1).name,200,258,21,INK,bold);center(c,"Due menti. Una classifica. Tante scoperte.",200,286,12,MUTED,regular);}else{
            center(c,""+score,174,290,66,INK,heavy);text(c,"/ 10",211,287,27,MUTED,bold);center(c,"risposte trovate · "+session.player(0).attemptsUsed()+" tentativi",200,322,12,MUTED,regular);
        }
        float y=duel?313:346;rect(c,24,y,352,63,18,0xffedf0e4);icon(c,"spark",39,y+19,23,GREEN);wrap(c,score>=7?"Il bello di sapere qualcosa? Scoprire quanto c’è ancora da sapere.":"Adesso sai dieci cose in più. La prossima vetta è già lì che ti aspetta.",78,y+25,278,12,18,INK,regular,2);y+=88;
        text(c,"La classifica, svelata.",25,y,21,INK,bold);y+=24;
        for(int i=0;i<10;i++){
            RankedList.Entry entry=session.ranking.entries.get(i);boolean found=session.player(0).isRevealed(i)||(duel&&session.player(1).isRevealed(i));
            rect(c,24,y,352,58,14,found?0xffeaf1d7:WHITE);circle(c,47,y+29,14,found?GREEN:0xffecefe6);center(c,position(i),47,y+33,12,found?WHITE:MUTED,bold);
            fit(c,entry.name,73,y+25,duel?229:279,14,INK,bold);fit(c,entry.value,73,y+43,279,10,MUTED,regular);
            if(duel){if(session.player(0).isRevealed(i))pill(c,"1",312,y+18,20,21,LIME,INK,10);if(session.player(1).isRevealed(i))pill(c,"2",339,y+18,20,21,LILAC,INK,10);}else if(found)text(c,"✓",346,y+35,18,GREEN,bold);
            read(24,y,352,58,"Posizione "+position(i)+". "+entry.name+". "+entry.value+(found?". Trovata.":""));
            y+=66;
        }
        y+=12;text(c,session.ranking.sourceName+" · "+session.ranking.referenceYear,25,y,11,MUTED,bold);y+=22;wrap(c,session.ranking.methodology,25,y,350,11,17,MUTED,regular,4);y+=80;
        pill(c,"CONSULTA LA FONTE ↗",24,y,352,42,0xffeeefe6,INK,10);hit(24,y,352,42,"Consulta la fonte dei dati",this::source);y+=60;
        button(c,"Un’altra Top 10",24,y,352,57,INK,WHITE,()->{pendingMode=session.mode;choose(pendingMode);});y+=71;button(c,"Torna alla home",24,y,352,48,0xffeeefe6,INK,()->{show("home");session=null;save();});
        pageEnd(c,y+70);
        // Results have their own sticky navigation, leaving the answers as a scrollable keepsake.
        rect(c,0,H-83,400,83,0,BG);line(c,24,H-83,376,H-83,LINE,1);button(c,"Un’altra Top 10  →",24,H-66,352,48,INK,WHITE,()->choose(session.mode));
    }

    private void nav(Canvas c,String active){
        float contentShift=pageShift;pageShift=0;
        if(gradientHeight!=H){gradientHeight=H;dockGradient=new LinearGradient(0,H-138,0,H-103,0x00f8f7f0,BG,Shader.TileMode.CLAMP);}
        if(!reduced()&&now()-navAt<380)frameAnimating=true;
        p.setStyle(Paint.Style.FILL);p.setShader(dockGradient);c.drawRect(0,H-138,400,H-103,p);p.setShader(null);
        float y=H-90;
        rect(c,29,y+11,342,71,31,0x09183b32);rect(c,26,y+6,348,72,32,0x12183b32);rect(c,24,y,352,72,31,INK);
        stroke(c,25,y+1,350,70,30,0x264f7562,1);
        float slot=110.66f,position=navPosition();rect(c,34+slot*position,y+9,slot,54,23,LIME);
        String[] labels={"Scopri","Classifiche","Percorso"},pages={"home","library","profile"},symbols={"home","grid","flag"};
        for(int i=0;i<3;i++){
            float x=34+slot*i+slot/2;
            float lit=Math.max(0,1-Math.abs(position-i));int color=blend(0xffc8d8c9,INK,lit);
            icon(c,symbols[i],x-10,y+17,20,color);center(c,labels[i],x,y+57,9.5f,color,bold);
            final String page=pages[i];hit(34+slot*i,y+4,slot,64,labels[i]+(active.equals(page)?", selezionata":""),()->{if(!screen.equals(page))show(page);});
        }
        pageShift=contentShift;
    }
    private int blend(int a,int b,float t){return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}

    private void modal(Canvas c){
        float progress=sheetProgress();if(!reduced()&&Motion.progress(now(),sheetAt,sheetClosing?240:360)<1)frameAnimating=true;
        c.drawColor((Math.round(136*progress*(1-Math.min(.7f,sheetDrag/sheetHeight())))<<24)|0x0d241a);
        if(!capturing)hits.clear();pageShift=0;float y=sheetTop();
        rect(c,0,y,400,H-y+30,30,BG);rect(c,178,y+10,44,4,2,0xffcbd2c3);
        circle(c,355,y+45,17,0xffeeefe6);text(c,"×",348,y+51,23,INK,regular);hit(328,y+22,51,45,"Chiudi",this::closeSheet);
        if(modal.equals("settings")){
            text(c,"Come piace a te.",25,y+65,27,INK,heavy);text(c,"Piccoli dettagli, una bella partita.",25,y+94,13,MUTED,regular);
            setting(c,y+122,"Suoni","Una nota per ogni scoperta", "sound",true);setting(c,y+192,"Vibrazione","Senti quando ci hai preso", "haptic",true);setting(c,y+262,"Animazioni ridotte","Un’esperienza più tranquilla", "reduced",false);
            updateCard(c,y+334);
            rect(c,24,y+451,352,53,17,0xffe9eede);icon(c,"play",40,y+467,20,INK);text(c,"Riguarda l’introduzione",76,y+483,13,INK,bold);chevron(c,350,y+477);hit(24,y+451,352,53,"Riguarda introduzione animata",this::openOnboarding);
            center(c,"TOPPERIA "+updater.currentVersionName()+" · FATTO PER MENTI CURIOSE",200,y+540,8,MUTED,bold);
        }else{
            text(c,"Dieci posti. Dieci tentativi.",25,y+67,25,INK,heavy);text(c,"Tutto qui. Il resto è curiosità.",25,y+98,14,MUTED,regular);
            rule(c,y+129,1,"Pensa alla Top 10","Scrivi un elemento che pensi sia nella classifica.");
            rule(c,y+208,2,"Scopri il tuo posto","Se c’è, la casella si gira. Ogni risposta nuova usa un tentativo.");
            rule(c,y+287,3,"Arriva più in alto","Dopo 10 tentativi scopri tutte le risposte. Vince chi ne trova di più.");
            text(c,"Ripetizioni e risposte vuote non consumano tentativi.",25,y+380,10,MUTED,regular);
            button(c,"Ci sono, giochiamo!",24,y+405,352,53,INK,WHITE,this::closeSheet);
        }
    }
    private void rule(Canvas c,float y,int n,String title,String body){circle(c,44,y+20,19,n==2?LILAC:LIME);center(c,""+n,44,y+25,16,INK,bold);text(c,title,78,y+16,16,INK,bold);wrap(c,body,78,y+39,292,12,17,MUTED,regular,2);}
    private float switchPosition(String key,boolean fallback){float target=store.option(key,fallback)?1:0;return !reduced()&&toggleKey.equals(key)?Motion.lerp(toggleFrom,target,Motion.ease(Motion.progress(now(),toggleAt,240))):target;}
    private void setting(Canvas c,float y,String title,String desc,String key,boolean fallback){
        text(c,title,25,y+21,16,INK,bold);text(c,desc,25,y+43,11,MUTED,regular);boolean on=store.option(key,fallback);float t=switchPosition(key,fallback);
        if(!reduced()&&toggleKey.equals(key)&&now()-toggleAt<240)frameAnimating=true;
        rect(c,320,y+5,54,30,15,blend(0xffdce2d4,GREEN,t));circle(c,335+24*t,y+20,11,WHITE);
        hit(20,y-4,359,63,title+(on?", attivo":", disattivo"),()->{toggleFrom=switchPosition(key,fallback);toggleKey=key;toggleAt=now();store.toggle(key,fallback);readMotionPreference();invalidate();});
    }
    private void updateCard(Canvas c,float y){
        rect(c,24,y,352,104,19,WHITE);text(c,"AGGIORNAMENTI",41,y+24,9,MUTED,bold);
        String label=updater.isBusy()?"Attendi…":updateReady?"Installa":availableUpdate!=null?"Scarica":"Verifica";
        pill(c,label,266,y+12,92,32,updater.isBusy()?0xffe9ede2:LIME,INK,11);hit(260,y+6,104,44,label+" aggiornamenti",this::updateAction);
        fit(c,availableUpdate==null?"Topperia "+updater.currentVersionName():"Disponibile "+availableUpdate.versionName,41,y+48,215,16,INK,bold);
        fit(c,updateStatus,41,y+71,314,11,MUTED,regular);
        if(downloadProgress>=0){rect(c,41,y+85,316,4,2,0xffe7ebdf);rect(c,41,y+85,316*Math.max(0,Math.min(1,downloadProgress/100f)),4,2,GREEN);}else text(c,"Distribuita dalla repository ufficiale su GitHub",41,y+91,9,MUTED,regular);
        read(30,y,224,102,"Aggiornamenti. Versione installata "+updater.currentVersionName()+". "+updateStatus);
    }

    private void pressTransform(Canvas c,float x,float y,float w,float h){c.save();if(pressedBounds!=null&&!reduced()&&Math.abs(pressedBounds.width()-w)<3&&Math.abs(pressedBounds.top-(y+hitOffset))<3){float t=pressRelease==0?1-Motion.ease(Motion.progress(now(),pressAt,220)):Motion.ease(Motion.progress(now(),pressAt,100));float size=1-.025f*t;c.scale(size,size,x+w/2,y+h/2);}}
    private void actionCard(Canvas c,float x,float y,float w,float h,int color,String symbol,String title,String subtitle,Runnable action){pressTransform(c,x,y,w,h);rect(c,x,y+3,w,h,23,0xffe4e7db);rect(c,x,y,w,h,23,color);iconBadge(c,symbol,x+14,y+17,47,0x50ffffff);text(c,title,x+77,y+34,18,INK,bold);text(c,subtitle,x+77,y+57,11,INK,regular);chevron(c,x+w-24,y+42);c.restore();hit(x,y,w,h,title,action);}
    private void button(Canvas c,String title,float x,float y,float w,float h,int bg,int fg,Runnable action){pressTransform(c,x,y,w,h);rect(c,x,y,w,h,18,bg);center(c,title,x+w/2,y+h/2+5,15,fg,bold);c.restore();hit(x,y,w,h,title,action);}
    private void logoMark(Canvas c,float x,float y,float size){if(logo!=null)c.drawBitmap(logo,null,new RectF(x,y,x+size,y+size),logoPaint);}
    private void brand(Canvas c,float x,float y,float size){c.save();c.translate(x,y);c.scale(size,size);logoMark(c,-7,-38,49);text(c,"topperia",43,-1,25,INK,heavy);c.restore();}
    private void podium(Canvas c,float x,float y){float bob=(float)Math.sin(anim()*1.6)*4;c.save();c.translate(x,y);circle(c,6,1,76,0xff426e4d);oval(c,-65,57,126,19,0x33132920);rect(c,-65,16,43,49,10,0xffa8c972);rect(c,-26,-4,50,69,10,LIME);rect(c,20,28,43,37,10,0xffbfda85);center(c,"2",-44,46,19,GREEN,heavy);center(c,"1",-1,27,24,GREEN,heavy);center(c,"3",42,53,17,GREEN,heavy);trophy(c,-1,-42+bob,ORANGE);circle(c,-70,-49,12,LILAC);center(c,"?",-70,-45,13,INK,bold);star(c,61,-62,9,LIME);star(c,61,3,4,LILAC);c.restore();}
    private void trophy(Canvas c,float x,float y,int color){if(c.quickReject(x-65,y-80,x+65,y+45,Canvas.EdgeType.AA))return;if(!reduced())frameAnimating=true;c.save();c.translate(x,y);float b=(float)Math.sin(anim()*1.8)*2;c.translate(0,b);stroke(c,-37,-24,74,34,16,color,5);p.setColor(color);Path cup=cupPath;cup.rewind();cup.moveTo(-28,-34);cup.lineTo(28,-34);cup.lineTo(22,0);cup.quadTo(0,26,-22,0);cup.close();c.drawPath(cup,p);rect(c,-4,10,8,18,3,color);rect(c,-19,26,38,8,4,color);circle(c,-8,-13,2.5f,INK);circle(c,8,-13,2.5f,INK);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.4f);p.setColor(INK);c.drawArc(-7,-12,7,1,15,150,false,p);p.setStyle(Paint.Style.FILL);star(c,43,-41,7,LILAC);c.restore();}
    private void iconBadge(Canvas c,String name,float x,float y,float s,int bg){rect(c,x,y,s,s,s*.3f,bg);icon(c,name,x+s*.24f,y+s*.24f,s*.52f,INK);}
    private void icon(Canvas c,String name,float x,float y,float size,int color){c.save();c.translate(x,y);c.scale(size/24,size/24);p.setColor(color);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);Path a=iconPath;a.rewind();
        switch(name){
            case "dice":c.drawRoundRect(2,2,22,22,5,5,p);for(float[] point:new float[][]{{7,7},{17,7},{12,12},{7,17},{17,17}}){c.drawCircle(point[0],point[1],.9f,p);}break;
            case "music":c.drawLine(9,5,9,18,p);c.drawLine(20,2,20,15,p);c.drawLine(9,5,20,2,p);c.drawLine(9,9,20,6,p);c.drawOval(3,16,9,22,p);c.drawOval(14,13,20,19,p);break;
            case "film":c.drawRoundRect(2,3,22,21,3,3,p);c.drawLine(7,3,7,21,p);c.drawLine(17,3,17,21,p);for(int i=0;i<3;i++){c.drawLine(2,8+i*5,7,8+i*5,p);c.drawLine(17,8+i*5,22,8+i*5,p);}break;
            case "gamepad":a.moveTo(6,6);a.cubicTo(2,6,0,21,4,21);a.cubicTo(7,21,8,16,10,16);a.lineTo(14,16);a.cubicTo(16,16,17,21,20,21);a.cubicTo(24,21,22,6,18,6);a.close();c.drawPath(a,p);c.drawLine(7,9,7,14,p);c.drawLine(4.5f,11.5f,9.5f,11.5f,p);c.drawCircle(16,10,.8f,p);c.drawCircle(19,13,.8f,p);break;
            case "coffee":c.drawRoundRect(3,8,17,20,3,3,p);c.drawArc(14,8,23,16,270,180,false,p);c.drawLine(2,23,20,23,p);c.drawLine(7,1,7,4,p);c.drawLine(13,1,13,4,p);break;
            case "plane":a.moveTo(11,2);a.quadTo(12,0,13,2);a.lineTo(14,9);a.lineTo(22,14);a.lineTo(22,16);a.lineTo(14,13);a.lineTo(14,19);a.lineTo(17,21);a.lineTo(17,23);a.lineTo(12,21);a.lineTo(7,23);a.lineTo(7,21);a.lineTo(10,19);a.lineTo(10,13);a.lineTo(2,16);a.lineTo(2,14);a.lineTo(10,9);a.close();c.drawPath(a,p);break;
            case "museum":a.moveTo(2,8);a.lineTo(12,2);a.lineTo(22,8);a.close();c.drawPath(a,p);for(int i=0;i<3;i++){c.drawLine(5+i*7,11,5+i*7,19,p);}c.drawLine(2,22,22,22,p);c.drawLine(3,19,21,19,p);break;
            case "atom":for(int i=0;i<3;i++){c.save();c.rotate(60*i,12,12);c.drawOval(2,7,22,17,p);c.restore();}c.drawCircle(12,12,2,p);break;
            case "water":for(int i=0;i<3;i++){a.reset();a.moveTo(1,7+i*5);a.cubicTo(5,1+i*5,7,13+i*5,12,7+i*5);a.cubicTo(17,1+i*5,19,13+i*5,23,7+i*5);c.drawPath(a,p);}break;
            case "gem":a.moveTo(2,8);a.lineTo(6,2);a.lineTo(18,2);a.lineTo(22,8);a.lineTo(12,22);a.close();c.drawPath(a,p);c.drawLine(2,8,22,8,p);c.drawLine(8,8,12,22,p);c.drawLine(16,8,12,22,p);break;
            case "park":case "leaf":a.moveTo(12,2);a.lineTo(4,12);a.lineTo(8,12);a.lineTo(2,19);a.lineTo(22,19);a.lineTo(16,12);a.lineTo(20,12);a.close();c.drawPath(a,p);c.drawLine(12,19,12,23,p);break;
            case "rocket":a.moveTo(12,1);a.cubicTo(5,5,7,13,8,17);a.lineTo(16,17);a.cubicTo(17,13,19,5,12,1);a.close();c.drawPath(a,p);c.drawCircle(12,8,2,p);c.drawLine(10,20,9,23,p);c.drawLine(14,20,15,23,p);c.drawLine(7,11,3,18,p);c.drawLine(17,11,21,18,p);break;
            case "back":a.moveTo(15,4);a.lineTo(7,12);a.lineTo(15,20);c.drawPath(a,p);break;
            case "play":a.moveTo(8,4);a.lineTo(19,12);a.lineTo(8,20);a.close();c.drawPath(a,p);break;
            case "info":c.drawCircle(12,12,9,p);c.drawLine(12,11,12,17,p);c.drawCircle(12,7,.7f,p);break;
            case "settings":c.drawCircle(12,12,4,p);c.drawCircle(12,12,9,p);for(int i=0;i<8;i++){double t=i*Math.PI/4;c.drawLine(12+(float)Math.cos(t)*9,12+(float)Math.sin(t)*9,12+(float)Math.cos(t)*11,12+(float)Math.sin(t)*11,p);}break;
            case "home":a.moveTo(3,10);a.lineTo(12,3);a.lineTo(21,10);a.lineTo(20,21);a.lineTo(15,21);a.lineTo(15,14);a.lineTo(9,14);a.lineTo(9,21);a.lineTo(4,21);a.close();c.drawPath(a,p);break;
            case "grid":for(int i=0;i<4;i++)c.drawRoundRect(3+(i%2)*11,3+(i/2)*11,10+(i%2)*11,10+(i/2)*11,2,2,p);break;
            case "solo":c.drawCircle(12,6,4,p);c.drawArc(3,12,21,27,180,180,false,p);break;
            case "duel":c.drawCircle(7,7,3.5f,p);c.drawCircle(18,8,3,p);c.drawArc(0,13,14,26,180,180,false,p);c.drawArc(13,14,24,25,180,180,false,p);break;
            case "beer":c.drawRoundRect(4,6,17,22,3,3,p);c.drawRoundRect(17,9,23,18,2,2,p);c.drawLine(8,11,8,18,p);c.drawLine(13,11,13,18,p);c.drawCircle(6,5,3,p);c.drawCircle(11,4,3,p);c.drawCircle(16,5,3,p);break;
            case "building":c.drawRect(5,8,19,22,p);c.drawRect(9,3,15,8,p);c.drawLine(12,0,12,3,p);for(int i=0;i<6;i++){float xx=9+i%2*6,yy=11+i/2*4;c.drawPoint(xx,yy,p);}break;
            case "mountain":a.moveTo(1,21);a.lineTo(10,3);a.lineTo(21,21);a.close();c.drawPath(a,p);a.reset();a.moveTo(7,10);a.lineTo(11,12);a.lineTo(14,10);c.drawPath(a,p);break;
            case "moon":c.drawCircle(12,12,10,p);c.drawCircle(8,8,2.4f,p);c.drawCircle(15,15,3,p);c.drawCircle(7,17,1,p);break;
            case "flag":c.drawLine(5,3,5,22,p);a.moveTo(5,4);a.cubicTo(11,0,15,9,21,5);a.lineTo(21,15);a.cubicTo(15,19,11,10,5,14);c.drawPath(a,p);break;
            case "trophy":c.drawRoundRect(5,2,19,15,4,4,p);c.drawArc(0,4,8,13,70,200,false,p);c.drawArc(16,4,24,13,270,200,false,p);c.drawLine(12,15,12,21,p);c.drawLine(7,22,17,22,p);break;
            case "star":case "spark":p.setStyle(Paint.Style.FILL);star(c,12,12,10,color);break;
            default:c.drawCircle(12,12,10,p);c.drawOval(7,2,17,22,p);c.drawLine(3,9,21,9,p);c.drawLine(3,15,21,15,p);break;
        }
        p.setStyle(Paint.Style.FILL);c.restore();
    }
    private void chevron(Canvas c,float x,float y){line(c,x-3,y-5,x+2,y,INK,1.6f);line(c,x+2,y,x-3,y+5,INK,1.6f);}
    private void star(Canvas c,float x,float y,float r,int color){Path a=starPath;a.rewind();for(int i=0;i<10;i++){double t=-Math.PI/2+i*Math.PI/5;float rr=i%2==0?r:r*.45f;float xx=x+(float)Math.cos(t)*rr,yy=y+(float)Math.sin(t)*rr;if(i==0)a.moveTo(xx,yy);else a.lineTo(xx,yy);}a.close();p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawPath(a,p);}
    private void rect(Canvas c,float x,float y,float w,float h,float radius,int color){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawRoundRect(x,y,x+w,y+h,radius,radius,p);}
    private void stroke(Canvas c,float x,float y,float w,float h,float radius,int color,float width){p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);c.drawRoundRect(x,y,x+w,y+h,radius,radius,p);p.setStyle(Paint.Style.FILL);}
    private void circle(Canvas c,float x,float y,float r,int color){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,r,p);}
    private void oval(Canvas c,float x,float y,float w,float h,int color){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawOval(x,y,x+w,y+h,p);}
    private void line(Canvas c,float x,float y,float xx,float yy,int color,float width){p.setColor(color);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x,y,xx,yy,p);}
    private void text(Canvas c,String s,float x,float y,float size,int color,Typeface face){p.setColor(color);p.setTypeface(face);p.setTextSize(size);p.setStyle(Paint.Style.FILL);c.drawText(s,x,y,p);}
    private float measure(String s,float size,Typeface face){p.setTypeface(face);p.setTextSize(size);return p.measureText(s);}
    private static final class FittedText{final String text;final float size;FittedText(String text,float size){this.text=text;this.size=size;}}
    private void fit(Canvas c,String s,float x,float y,float width,float size,int color,Typeface face){
        if(c.quickReject(x,y-size,x+width,y+size*.3f,Canvas.EdgeType.AA))return;
        String key=s+'\n'+width+':'+size+':'+(face==heavy?2:face==bold?1:0);FittedText fitted=fittedText.get(key);
        if(fitted==null){while(size>8&&measure(s,size,face)>width)size=Math.max(8,size-.3f);if(measure(s,size,face)>width){while(s.length()>1&&measure(s+"…",size,face)>width)s=s.substring(0,s.length()-1);s+="…";}fitted=new FittedText(s,size);if(fittedText.size()>320)fittedText.clear();fittedText.put(key,fitted);}
        text(c,fitted.text,x,y,fitted.size,color,face);
    }
    private void center(Canvas c,String s,float x,float y,float size,int color,Typeface face){text(c,s,x-measure(s,size,face)/2,y,size,color,face);}
    private void centerFit(Canvas c,String s,float x,float y,float width,float size,int color,Typeface face){while(size>13&&measure(s,size,face)>width)size-=.5f;center(c,s,x,y,size,color,face);}
    private void pill(Canvas c,String title,float x,float y,float w,float h,int bg,int fg,float size){if(bg!=0)rect(c,x,y,w,h,h/2,bg);center(c,title,x+w/2,y+h/2+size*.35f,size,fg,bold);}
    private float wrap(Canvas c,String value,float x,float y,float width,float size,float spacing,int color,Typeface face,int max){
        if(c.quickReject(x,y-size,x+width,y+max*spacing,Canvas.EdgeType.AA))return y+max*spacing;
        String key=value+'\n'+width+':'+size+':'+max+':'+(face==heavy?2:face==bold?1:0);
        String[] lines=wrappedText.get(key);
        if(lines==null){
            ArrayList<String> rows=new ArrayList<>();String row="";
            for(String word:value.split(" ")){if(!row.isEmpty()&&measure(row+" "+word,size,face)>width){rows.add(row);row="";if(rows.size()==max)break;}row+=row.isEmpty()?word:" "+word;}
            if(!row.isEmpty()&&rows.size()<max)rows.add(row);
            lines=rows.toArray(new String[0]);if(wrappedText.size()>240)wrappedText.clear();wrappedText.put(key,lines);
        }
        for(int i=0;i<lines.length;i++)text(c,lines[i],x,y+i*spacing,size,color,face);
        return y+lines.length*spacing;
    }
    private void hit(float x,float y,float w,float h,String description,Runnable action){if(capturing)return;RectF b=new RectF(x+pageShift,y+hitOffset,x+w+pageShift,y+h+hitOffset);if(contentClip&&!b.intersect(0,0,400,H-DOCK_SPACE))return;hits.add(new Hit(b,description,action));}
    private void read(float x,float y,float w,float h,String description){hit(x,y,w,h,description,null);}
    private static final class Hit{final RectF bounds;final String label;final Runnable action;Hit(RectF b,String s,Runnable a){bounds=b;label=s;action=a;}}
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            boolean moving=!scroller.isFinished();stopScroll();activePointer=event.getPointerId(0);velocity=VelocityTracker.obtain();velocity.addMovement(event);
            float x=event.getX()/scale,y=event.getY()/scale;downX=x;downY=lastY=y;dragging=moving;
            scrollingGesture=modal.isEmpty()&&maxScroll>0&&y<H-DOCK_SPACE;
            sheetGesture=!modal.isEmpty()&&!sheetClosing&&y>=sheetTop()&&y<=sheetTop()+110;
            clearPress();if(!moving&&!sheetClosing){for(int i=hits.size()-1;i>=0;i--){Hit hit=hits.get(i);if(hit.action!=null&&hit.bounds.contains(x,y)){pressedBounds=new RectF(hit.bounds);pressAt=now();break;}}}
            invalidate();return true;
        }
        if(velocity!=null)velocity.addMovement(event);
        if(action==MotionEvent.ACTION_POINTER_UP&&event.getPointerId(event.getActionIndex())==activePointer){int index=event.getActionIndex()==0?1:0;activePointer=event.getPointerId(index);lastY=event.getY(index)/scale;downX=event.getX(index)/scale;downY=lastY;dragging=true;clearPress();postInvalidateOnAnimation();return true;}
        int index=event.findPointerIndex(activePointer);if(index<0)index=0;
        float x=event.getX(index)/scale,y=event.getY(index)/scale;
        switch(action){
            case MotionEvent.ACTION_MOVE:
                if(!dragging&&(Math.abs(y-downY)*scale>touchSlop||Math.abs(x-downX)*scale>touchSlop)){dragging=true;clearPress();}
                if(dragging){if(sheetGesture)sheetDrag=Math.max(0,y-downY);else if(scrollingGesture)scroll=Math.max(0,Math.min(maxScroll,scroll+lastY-y));postInvalidateOnAnimation();}
                lastY=y;return true;
            case MotionEvent.ACTION_UP:
                if(velocity!=null)velocity.computeCurrentVelocity(1000,maxFling);
                float speed=velocity==null?0:velocity.getYVelocity(activePointer)/scale;
                if(sheetGesture&&dragging){if(sheetDrag>70||speed>900){closeSheet();}else{sheetFrom=Math.max(0,sheetProgress()-sheetDrag/sheetHeight());sheetTarget=1;sheetAt=now();sheetDrag=0;}}
                else if(dragging&&scrollingGesture&&!reduced()&&Math.abs(speed)>minFling/scale){scroller.fling(0,Math.round(scroll*scale),0,Math.round(-speed*scale),0,0,0,Math.round(maxScroll*scale));postInvalidateOnAnimation();}
                else if(screen.equals("onboarding")&&modal.isEmpty()&&Math.abs(x-downX)>50&&Math.abs(x-downX)>Math.abs(y-downY)*1.3f){onboardingPage(onboardingStep+(x<downX?1:-1));}
                else if(!dragging&&!sheetClosing){if(!modal.isEmpty()&&y<sheetTop())closeSheet();else{for(int i=hits.size()-1;i>=0;i--){Hit hit=hits.get(i);if(hit.action!=null&&hit.bounds.contains(x,y)&&hit.bounds.contains(downX,downY)){tappedAction=hit.action;performClick();break;}}}}
                releasePress();if(velocity!=null){velocity.recycle();velocity=null;}activePointer=-1;sheetGesture=false;postInvalidateOnAnimation();return true;
            case MotionEvent.ACTION_CANCEL:
                if(sheetDrag>0){sheetFrom=Math.max(0,sheetProgress()-sheetDrag/sheetHeight());sheetTarget=1;sheetAt=now();sheetDrag=0;}stopScroll();sheetGesture=false;clearPress();invalidate();return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();if(tappedAction!=null){Runnable action=tappedAction;tappedAction=null;action.run();invalidate();return true;}return false;}

    private void accessEvent(int id,int type){AccessibilityEvent event=AccessibilityEvent.obtain(type);event.setSource(this,id);event.setPackageName(activity.getPackageName());if(id>0&&id<=hits.size())event.setContentDescription(hits.get(id-1).label);if(getParent()!=null)getParent().requestSendAccessibilityEvent(this,event);}
    @Override public boolean dispatchHoverEvent(MotionEvent event){android.view.accessibility.AccessibilityManager manager=(android.view.accessibility.AccessibilityManager)activity.getSystemService(Context.ACCESSIBILITY_SERVICE);if(manager.isEnabled()&&manager.isTouchExplorationEnabled()){int target=-1;if(event.getAction()!=MotionEvent.ACTION_HOVER_EXIT){float x=event.getX()/scale,y=event.getY()/scale;for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).bounds.contains(x,y)){target=i+1;break;}}if(target!=hovered){if(hovered>0)accessEvent(hovered,AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);hovered=target;if(target>0)accessEvent(target,AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);}return target>0||super.dispatchHoverEvent(event);}return super.dispatchHoverEvent(event);}

    /** Virtual native controls keep the canvas navigable with TalkBack and switches. */
    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider(){return new AccessibilityNodeProvider(){
        @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id){
            if(id==View.NO_ID){AccessibilityNodeInfo node=AccessibilityNodeInfo.obtain(TopperiaView.this);onInitializeAccessibilityNodeInfo(node);for(int i=0;i<hits.size();i++)if(hits.get(i).bounds.bottom>0&&hits.get(i).bounds.top<H)node.addChild(TopperiaView.this,i+1);if(input.getVisibility()==VISIBLE)node.addChild(input);if(submit.getVisibility()==VISIBLE)node.addChild(submit);if(modal.isEmpty()&&maxScroll>0){node.setScrollable(true);node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}return node;}
            if(id<1||id>hits.size())return null;Hit hit=hits.get(id-1);AccessibilityNodeInfo node=AccessibilityNodeInfo.obtain();node.setSource(TopperiaView.this,id);node.setParent(TopperiaView.this);node.setPackageName(activity.getPackageName());node.setClassName(hit.action!=null?"android.widget.Button":"android.widget.TextView");node.setContentDescription(hit.label);node.setEnabled(true);node.setVisibleToUser(hit.bounds.bottom>0&&hit.bounds.top<H);node.setClickable(hit.action!=null);node.setFocusable(true);node.setAccessibilityFocused(accessibilityFocus==id);if(hit.action!=null)node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);node.addAction(accessibilityFocus==id?AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS:AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);Rect b=new Rect((int)(hit.bounds.left*scale),(int)(hit.bounds.top*scale),(int)(hit.bounds.right*scale),(int)(hit.bounds.bottom*scale));node.setBoundsInParent(b);int[] location=new int[2];getLocationOnScreen(location);b.offset(location[0],location[1]);node.setBoundsInScreen(b);return node;
        }
        @Override public boolean performAction(int id,int action,Bundle args){if(id==View.NO_ID&&(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)){if(!modal.isEmpty()||maxScroll<=0)return false;stopScroll();float target=Math.max(0,Math.min(maxScroll,scroll+(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1)*H*.6f));if(reduced())scroll=target;else scroller.startScroll(0,Math.round(scroll*scale),0,Math.round((target-scroll)*scale),300);postInvalidateOnAnimation();sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED);return true;}if(id>0&&id<=hits.size()){if(action==AccessibilityNodeInfo.ACTION_CLICK&&!sheetClosing&&hits.get(id-1).action!=null){hits.get(id-1).action.run();invalidate();return true;}if(action==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS){accessibilityFocus=id;accessEvent(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);return true;}if(action==AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS){accessibilityFocus=-1;accessEvent(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);return true;}}return false;}
    };}
}
