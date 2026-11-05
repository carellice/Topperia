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
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
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

/**
 * A native, resolution-independent little game world with offline rounds.
 * Everything is drawn on a 400-unit wide canvas over an animated night backdrop;
 * only text fields are real Android views, positioned on top of their drawn frames.
 */
public final class TopperiaView extends ViewGroup {
    private static final int BG=0xff0a1712, BG_TOP=0xff11291f, CARD=0xff152d25, CARD_HI=0xff1f3d34, SHEET=0xff11251e,
        LINE=0x24ffffff, TEXT=0xfff2f7e9, MUTED=0xff9cb3a6, DIM=0xff5e7b6d, LIME=0xffdcf58d, TEAL=0xff7fdcc0,
        LILAC=0xffc4b2f7, ORANGE=0xfff6b38a, PINK=0xfff39cc3, RED=0xffff8b84, INK=0xff0c2019, WHITE=0xffffffff;
    private static final int[] ACCENTS={LIME,LILAC,ORANGE,TEAL,PINK};
    private static final float DOCK_SPACE=108, CARD_H=178;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);
    private final Paint logoPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
    private final Bitmap logo;
    private final Typeface regular=Typeface.create("sans-serif",Typeface.NORMAL), bold=Typeface.create("sans-serif",Typeface.BOLD), heavy=Typeface.create("sans-serif-black",Typeface.NORMAL);
    private final Activity activity;
    private final GameStore store;
    private final List<RankedList> rankings;
    private final ArrayList<Hit> hits=new ArrayList<>();
    private final EditText input,nameOne,nameTwo;
    private final Button submit;
    private ToneGenerator tones;
    private final GitHubUpdater updater;
    private final UpdatePromptQueue updatePrompts;
    private UpdateInfo availableUpdate;
    private String updateStatus="Controlla se ci sono nuove versioni.";
    private boolean manualUpdateCheck=false,updateReady=false;
    private int downloadProgress=-1;
    private GameSession session;
    private String screen="home", filter="Tutte", modal="", notice="", duelReturn="home";
    private GameSession.Mode pendingMode=GameSession.Mode.SOLO;
    private float scale=1,H=820,scroll=0,maxScroll=0,hitOffset=0,clipBottom=820,downY,downX,lastY,alphaMul=1;
    private boolean dragging=false,recorded=false,keyboard=false,contentClip=false;
    private int accessibilityFocus=-1,hovered=-1,revealCount=0;
    private Runnable tappedAction;
    private long entered=SystemClock.uptimeMillis(),noticeAt=0,finishedAt=0,lastSubmissionAt=0,missAt=0,revealAt=0;
    private final long[] reveals=new long[10],seals=new long[10];
    private int lastHit=-1;
    private final Random random=new Random();
    private final RankingPicker picker=new RankingPicker(random);
    private final OverScroller scroller;
    private final int touchSlop,minFling,maxFling;
    private VelocityTracker velocity;
    private int activePointer=-1;
    private boolean scrollingGesture=false,sheetGesture=false,sheetClosing=false,capturing=false,frameReduced=false,frameAnimating=false;
    private long frameTime,pageAt=0,sheetAt=0,pressAt=0,filterAt=0,toggleAt=0,navAt=0,dockAt=0;
    private Picture previousPage;
    private float pageDirection=1,pageShift=0,sheetFrom=0,sheetTarget=0,sheetDrag=0;
    private RectF pressedBounds;
    private float pressRelease=1;
    private String toggleKey="";
    private float toggleFrom=0,navFrom=0,navTarget=0,dockFrom=1,dockTarget=1;
    private final ArrayList<String> categories=new ArrayList<>();
    private final HashMap<String,String[]> wrappedText=new HashMap<>();
    private final HashMap<String,FittedText> fittedText=new HashMap<>();
    private final Path iconPath=new Path(),starPath=new Path(),cupPath=new Path(),clipPath=new Path();
    private final Matrix shaderMatrix=new Matrix(),tilt=new Matrix();
    private final Camera camera=new Camera();
    private final DashPathEffect dashes=new DashPathEffect(new float[]{6,6},0);
    private LinearGradient backdropShader,dockFade;
    private RadialGradient[] blobs;
    private float gradientHeight=-1;
    private int onboardingStep=0,shuffleGeneration=0;
    private String onboardingReturn="home",shuffleReturn="home";
    private boolean onboardingReplay=false;
    private RankedList queuedRanking,duelRanking,detailRanking;

    public TopperiaView(Context context) {
        super(context); activity=(Activity)context; store=new GameStore(context); rankings=Rankings.all();
        updatePrompts=new UpdatePromptQueue(activity,this);
        scroller=new OverScroller(context);
        ViewConfiguration config=ViewConfiguration.get(context);touchSlop=config.getScaledTouchSlop();minFling=config.getScaledMinimumFlingVelocity();maxFling=config.getScaledMaximumFlingVelocity();
        categories.add("Tutte");for(RankedList ranking:rankings)if(!categories.contains(ranking.category))categories.add(ranking.category);
        BitmapFactory.Options logoOptions=new BitmapFactory.Options();logoOptions.inScaled=false;
        logo=BitmapFactory.decodeResource(getResources(),R.drawable.topperia_logo,logoOptions);
        setWillNotDraw(false); setFocusable(true); setFocusableInTouchMode(true);
        // Key events leave touch mode; the default highlight would veil the whole canvas.
        setDefaultFocusHighlightEnabled(false);
        camera.setLocation(0,0,-14);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        input=field(context,"La tua risposta…","Scrivi un elemento della Top 10",GameSession.MAX_INPUT_LENGTH,EditorInfo.IME_ACTION_GO);
        input.setOnEditorActionListener((v,action,event)->{if(event!=null&&event.getAction()!=KeyEvent.ACTION_DOWN)return true;if(action==EditorInfo.IME_ACTION_GO || (event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){guess();return true;}return false;});
        // The label follows the face of the 3D background as it sinks.
        submit=new Button(context){@Override protected void drawableStateChanged(){super.drawableStateChanged();int depth=(int)dp(4);if(isPressed())setPadding(0,depth,0,0);else setPadding(0,0,0,depth);}};
        submit.setText("↑"); submit.setTextSize(25); submit.setTextColor(INK); submit.setTypeface(bold);
        submit.setAllCaps(false); submit.setPadding(0,0,0,0); submit.setMinWidth(0); submit.setMinimumWidth(0); submit.setMinHeight(0); submit.setMinimumHeight(0);
        submit.setBackground(pushable(LIME,(int)dp(4)));
        submit.setStateListAnimator(null);
        submit.setContentDescription("Conferma risposta"); submit.setOnClickListener(v->guess()); addView(submit);
        nameOne=field(context,"Giocatore 1","Nome del primo giocatore",16,EditorInfo.IME_ACTION_NEXT);
        nameTwo=field(context,"Giocatore 2","Nome del secondo giocatore",16,EditorInfo.IME_ACTION_DONE);
        nameTwo.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){hideKeyboard();invalidate();return true;}return false;});
        restore();
        if(!store.option("onboarding_complete",false)){screen="onboarding";dockFrom=dockTarget=0;}
        updater=createUpdater();
        UpdateInfo cached=updater.downloadedUpdate();
        if(cached!=null){availableUpdate=cached;updateReady=true;downloadProgress=100;updateStatus="Aggiornamento scaricato. Pronto da installare.";}
        updateInput();
    }

    private EditText field(Context context,String hint,String description,int length,int action){
        EditText e=new EditText(context);
        e.setSingleLine(true); e.setTextSize(16); e.setTextColor(TEXT); e.setHintTextColor(DIM); e.setTypeface(bold);
        e.setHint(hint); e.setBackgroundColor(Color.TRANSPARENT); e.setDefaultFocusHighlightEnabled(false); e.setPadding(0,0,0,0); e.setGravity(Gravity.CENTER_VERTICAL);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(length)});
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setImeOptions(action|EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        e.setContentDescription(description);
        if(android.os.Build.VERSION.SDK_INT>=29){GradientDrawable cursor=new GradientDrawable();cursor.setColor(LIME);cursor.setSize((int)dp(2),(int)dp(20));e.setTextCursorDrawable(cursor);}
        e.setOnFocusChangeListener((v,focused)->invalidate());
        e.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){}
            @Override public void afterTextChanged(Editable s){invalidate();}
        });
        addView(e);return e;
    }

    /** A native push button: a darker base with a lit face that drops onto it while pressed. */
    private android.graphics.drawable.Drawable pushable(int color,int depth){
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},pushLayer(color,depth,true));
        states.addState(new int[]{},pushLayer(color,depth,false));
        return states;
    }
    private android.graphics.drawable.Drawable pushLayer(int color,int depth,boolean pressed){
        GradientDrawable base=new GradientDrawable();base.setColor(darken(color,.55f));base.setCornerRadius(dp(18));
        GradientDrawable face=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{lighten(color,.2f),color});face.setCornerRadius(dp(18));
        android.graphics.drawable.LayerDrawable layers=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{base,face});
        layers.setLayerInset(1,0,pressed?depth:0,0,pressed?0:depth);
        return layers;
    }
    private AlertDialog.Builder dialog(){return new AlertDialog.Builder(activity,android.R.style.Theme_Material_Dialog_Alert);}
    private GitHubUpdater createUpdater(){return new GitHubUpdater(activity,new GitHubUpdater.Listener(){
        @Override public void onChecking(){updateStatus="Controllo degli aggiornamenti…";invalidate();}
        @Override public void onUpToDate(String version){manualUpdateCheck=false;availableUpdate=null;updateReady=false;downloadProgress=-1;updateStatus="Hai già la versione più recente.";invalidate();tell("Topperia è aggiornato alla versione "+version+".");}
        @Override public void onUpdateAvailable(UpdateInfo info){boolean manual=manualUpdateCheck;manualUpdateCheck=false;UpdateInfo cached=updater.downloadedUpdate();availableUpdate=info;updateReady=cached!=null&&cached.sha256.equals(info.sha256);downloadProgress=updateReady?100:-1;updateStatus=updateReady?"Aggiornamento scaricato. Pronto da installare.":"Disponibile la versione "+info.versionName+".";invalidate();if(manual||!busyScreen()){if(updateReady)offerInstall(info);else offerUpdate(info);}}
        @Override public void onDownloadProgress(int progress){downloadProgress=progress;updateStatus=progress<0?"Scaricamento in corso…":"Scaricamento in corso · "+progress+"%";invalidate();}
        @Override public void onReadyToInstall(UpdateInfo info){availableUpdate=info;updateReady=true;downloadProgress=100;updateStatus="Aggiornamento verificato. Pronto da installare.";invalidate();offerInstall(info);}
        @Override public void onInstallPermissionRequired(){updatePrompts.post(()->updatePrompts.show(dialog().setTitle("Consenti gli aggiornamenti").setMessage("Android richiede il permesso di installare aggiornamenti da Topperia. Apri l’impostazione e abilita il consenso: poi potrai confermare l’installazione.").setNegativeButton("Più tardi",(d,w)->updater.cancelPendingInstall()).setPositiveButton("Apri impostazione",(d,w)->updater.requestInstallPermission())));}
        @Override public void onInstallerOpened(){updateStatus="Conferma l’aggiornamento nella schermata Android.";invalidate();}
        @Override public void onError(String message){manualUpdateCheck=false;downloadProgress=-1;UpdateInfo cached=updater.downloadedUpdate();updateReady=cached!=null&&availableUpdate!=null&&cached.sha256.equals(availableUpdate.sha256);updateStatus=message;invalidate();updatePrompts.post(()->updatePrompts.show(dialog().setTitle("Aggiornamento non completato").setMessage(message).setPositiveButton("Ho capito",null)));}
    });}
    private boolean busyScreen(){return screen.equals("game")||screen.equals("onboarding")||screen.equals("shuffle")||screen.equals("reveal")||screen.equals("duel");}
    private void offerUpdate(UpdateInfo info){
        updatePrompts.post(()->{
        String notes=info.notes==null?"":info.notes.replaceAll("(?m)^#{1,6}\\s*","").replace("**","").trim();if(notes.length()>1000)notes=notes.substring(0,1000)+"…";
        String size=String.format(Locale.ITALIAN,"%.1f MB",info.sizeBytes/1048576f);
        updatePrompts.show(dialog().setTitle("Topperia "+info.versionName+" è pronta").setMessage("Puoi scaricare l’aggiornamento da GitHub ("+size+"). I tuoi salvataggi restano al loro posto."+(notes.isEmpty()?"":"\n\n"+notes)).setNegativeButton("Più tardi",null).setPositiveButton("Scarica",(d,w)->{downloadProgress=0;updateStatus="Preparazione del download…";invalidate();updater.beginDownload(info);}));
        });
    }
    private void offerInstall(UpdateInfo info){updatePrompts.post(()->updatePrompts.show(dialog().setTitle("Tutto pronto per aggiornare").setMessage("Topperia "+info.versionName+" è stata scaricata e verificata. Android ti chiederà conferma per installarla.").setNegativeButton("Più tardi",null).setPositiveButton("Installa",(d,w)->updater.installDownloaded())));}
    private void updateAction(){if(updater.isBusy())return;if(updateReady){offerInstall(availableUpdate);return;}if(availableUpdate!=null){offerUpdate(availableUpdate);return;}manualUpdateCheck=true;updater.check(true);}
    public void resume(){readMotionPreference();invalidate();updatePrompts.onResume();updater.onResume();updater.check(false);}
    public void pause(){updatePrompts.onPause();stopScroll();clearPress();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);updatePrompts.onWindowFocusChanged(focused);}

    private float dp(float x){return x*getResources().getDisplayMetrics().density;}
    private boolean reduced(){return frameReduced;}
    private void readMotionPreference(){frameReduced=store.option("reduced",false)||!android.animation.ValueAnimator.areAnimatorsEnabled();}
    private long now(){return SystemClock.uptimeMillis();}
    private float anim(){return reduced()?0:(frameTime%200000)/1000f;}
    private boolean dockPage(String page){return page.equals("home")||page.equals("library")||page.equals("profile");}
    private int pageIndex(String page){return page.equals("home")?0:page.equals("library")?1:2;}
    private String pageTitle(String page){switch(page){case "home":return "Gioca";case "library":return "Classifiche";case "profile":return "Profilo";case "pick":return "Scegli la classifica";case "duel":return "Sfida 1 contro 1";case "results":return "Risultato";case "reveal":return "La resa dei conti";case "onboarding":return "Benvenuto";case "shuffle":return "Una sorpresa";default:return "La tua scalata";}}

    // The outgoing page is recorded without its backdrop: the animated sky stays continuous
    // while the old content fades away above the new one.
    private void capturePage(){
        if(capturing)return;
        if(getWidth()==0||reduced()||((screen.equals("game")||screen.equals("results")||screen.equals("reveal"))&&session==null)) {previousPage=null;return;}
        Picture picture=new Picture();Canvas c=picture.beginRecording(400,(int)Math.ceil(H));
        capturing=true;float before=alphaMul;alphaMul=1;
        try{
            drawPageFrame(c);
            c.save();c.scale(1/scale,1/scale);super.dispatchDraw(c);c.restore();
            if(!modal.isEmpty())modal(c);
        }finally{picture.endRecording();capturing=false;hitOffset=0;contentClip=false;alphaMul=before;}
        previousPage=picture;
    }
    private void show(String page){
        readMotionPreference();frameTime=now();capturePage();stopScroll();clearPress();
        pageDirection=dockPage(screen)&&dockPage(page)?Math.signum(pageIndex(page)-pageIndex(screen)):page.equals("home")?-1:1;
        if(pageDirection==0)pageDirection=1;
        if(dockPage(page)){navFrom=navPosition();navTarget=pageIndex(page);navAt=now();}
        float dock=dockPage(page)?1:0;if(dock!=dockTarget){dockFrom=dockProgress();dockTarget=dock;dockAt=now();}
        if(page.equals("duel")){nameOne.setText(storedName("player1","Giocatore 1"));nameTwo.setText(storedName("player2","Giocatore 2"));}
        if(page.equals("reveal")){revealAt=now()+300;revealCount=0;}
        screen=page;scroll=0;maxScroll=0;entered=pageAt=now();modal="";sheetClosing=false;sheetDrag=0;pageShift=0;
        hits.clear();accessibilityFocus=-1;hovered=-1;hideKeyboard();updateInput();requestLayout();invalidate();
        activity.setTitle("Topperia · "+pageTitle(page));
        if(android.os.Build.VERSION.SDK_INT>=28)setAccessibilityPaneTitle(activity.getTitle());sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }
    private String storedName(String key,String fallback){String name=store.prefs.getString(key,"");return name.equals(fallback)?"":name;}
    private float navPosition(){float t=reduced()?1:Math.min(1,(now()-navAt)/420f);return navFrom+(navTarget-navFrom)*(1-(float)Math.pow(1-t,3));}
    private float dockProgress(){return reduced()?dockTarget:Motion.lerp(dockFrom,dockTarget,Motion.ease(Motion.progress(now(),dockAt,380)));}
    private void updateInput(){
        boolean typing=screen.equals("game")&&session!=null&&session.phase()==GameSession.Phase.PLAYING&&modal.isEmpty();
        input.setVisibility(typing?VISIBLE:GONE);submit.setVisibility(typing?VISIBLE:GONE);
        if(typing)input.setHint(session.isBlind()?"Sigilla una risposta…":"La tua risposta…");
        boolean names=screen.equals("duel")&&modal.isEmpty();
        nameOne.setVisibility(names?VISIBLE:GONE);nameTwo.setVisibility(names?VISIBLE:GONE);
    }
    private void hideKeyboard(){input.clearFocus();nameOne.clearFocus();nameTwo.clearFocus();((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(getWindowToken(),0);}
    private void feedback(boolean good){if(store.option("haptic",true)){int effect=android.os.Build.VERSION.SDK_INT>=30?(good?HapticFeedbackConstants.CONFIRM:HapticFeedbackConstants.REJECT):(good?HapticFeedbackConstants.KEYBOARD_TAP:HapticFeedbackConstants.LONG_PRESS);performHapticFeedback(effect);}tone(good?ToneGenerator.TONE_PROP_ACK:ToneGenerator.TONE_PROP_NACK,good?120:90);}
    private void sealFeedback(){if(store.option("haptic",true))performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);tone(ToneGenerator.TONE_PROP_BEEP,60);}
    private void tone(int tone,int duration){if(!store.option("sound",true))return;try{if(tones==null)tones=new ToneGenerator(AudioManager.STREAM_MUSIC,35);tones.startTone(tone,duration);}catch(RuntimeException ignored){}}
    private void tell(String text){notice=text;noticeAt=now();invalidate();announceForAccessibility(text);}

    private void start(RankedList list){
        if(session!=null&&session.phase()!=GameSession.Phase.FINISHED){dialog().setTitle("Una nuova scalata?").setMessage("Iniziando questa Top 10 sostituirai la partita salvata.").setNegativeButton("Annulla",null).setPositiveButton("Inizia",(d,w)->{session=null;start(list);}).show();return;}
        boolean duel=pendingMode==GameSession.Mode.DUEL;
        session=new GameSession(list,pendingMode,duel?store.prefs.getString("player1","Giocatore 1"):"Tu",store.prefs.getString("player2","Giocatore 2"));
        store.prefs.edit().putString("last_ranking",list.id).apply();recorded=false;finishedAt=0;lastHit=-1;Arrays.fill(reveals,0);Arrays.fill(seals,0);input.setText("");show("game");save();
    }
    private void randomRound(String category){
        if(session!=null&&session.phase()!=GameSession.Phase.FINISHED){dialog().setTitle("Una nuova sorpresa?").setMessage("La classifica casuale sostituirà la partita salvata.").setNegativeButton("Annulla",null).setPositiveButton("Sorprendimi",(d,w)->{session=null;save();randomRound(category);}).show();return;}
        ArrayList<RankedList> pool=new ArrayList<>();Set<String> played=new HashSet<>();for(RankedList r:rankings){if(category.equals("Tutte")||r.category.equals(category))pool.add(r);if(store.option("played_"+r.id,false))played.add(r.id);}
        if(pool.isEmpty()){tell("In questa categoria non ci sono ancora classifiche.");return;}
        queuedRanking=picker.pick(pool,played,store.prefs.getString("last_ranking",""));shuffleReturn=screen;show("shuffle");int generation=++shuffleGeneration;
        postDelayed(()->{if(screen.equals("shuffle")&&generation==shuffleGeneration&&queuedRanking!=null){RankedList selected=queuedRanking;queuedRanking=null;start(selected);}},reduced()?250:2350);
    }
    private void openOnboarding(){onboardingReturn=screen;onboardingReplay=true;onboardingStep=0;show("onboarding");}
    private void finishOnboarding(){store.prefs.edit().putBoolean("onboarding_complete",true).apply();show(onboardingReplay?onboardingReturn:"home");onboardingReplay=false;}
    private void onboardingPage(int step){if(step>=3){finishOnboarding();return;}readMotionPreference();capturePage();pageDirection=step>onboardingStep?1:-1;onboardingStep=Math.max(0,step);entered=pageAt=now();clearPress();accessibilityFocus=-1;hovered=-1;invalidate();sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
    private void choose(GameSession.Mode mode){
        pendingMode=mode;filter="Tutte";
        if(mode==GameSession.Mode.DUEL){duelRanking=null;duelReturn=screen;show("duel");}else show("pick");
    }
    private void openDetail(RankedList ranking){detailRanking=ranking;openSheet("detail");}
    private void playDetail(GameSession.Mode mode){
        RankedList ranking=detailRanking;if(ranking==null)return;
        if(mode==GameSession.Mode.DUEL){pendingMode=mode;duelRanking=ranking;duelReturn=screen;show("duel");}else{pendingMode=mode;start(ranking);}
    }
    private void saveNames(){String a=nameOne.getText().toString().trim(),b=nameTwo.getText().toString().trim();store.prefs.edit().putString("player1",a.isEmpty()?"Giocatore 1":a).putString("player2",b.isEmpty()?"Giocatore 2":b).apply();}
    private void duelContinue(){
        saveNames();hideKeyboard();pendingMode=GameSession.Mode.DUEL;
        if(duelRanking!=null){RankedList ranking=duelRanking;duelRanking=null;start(ranking);}else{filter="Tutte";show("pick");}
    }
    private void playAgain(){if(session==null){show("home");return;}if(session.mode==GameSession.Mode.DUEL){pendingMode=GameSession.Mode.DUEL;duelRanking=null;duelReturn="results";show("duel");}else choose(GameSession.Mode.SOLO);}
    private RankedList daily(){long day=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Rome")).toEpochDay();return rankings.get((int)Math.floorMod(day,rankings.size()));}
    private void guess(){
        if(session==null||!screen.equals("game"))return;
        if(now()-lastSubmissionAt<220)return;
        boolean blind=session.isBlind();
        boolean handoffNext=blind&&session.activePlayerIndex()==0&&session.currentPlayer().attemptsRemaining()==1;
        if(handoffNext)capturePage();
        String answer=input.getText().toString(); GameSession.GuessResult result=session.submit(answer);
        switch(result.status){
            case EMPTY:previousPage=null;tell(blind?"Scrivi una risposta da sigillare.":"Scrivi una risposta per iniziare.");return;
            case DUPLICATE:previousPage=null;tell(blind?"Hai già scritto questa risposta.":"Già provata. Conservi il tentativo!");input.selectAll();return;
            case INACTIVE:previousPage=null;return;
            case HIT:case MISS:
                if(blind){int slot=session.currentPlayer().attemptsUsed()-1;seals[slot]=now();sealFeedback();tell("Risposta sigillata · "+(slot+1)+" di 10");}
                else if(result.status==GameSession.GuessStatus.HIT){reveals[result.entryIndex]=now();lastHit=result.entryIndex;feedback(true);tell("Dentro! #"+position(result.entryIndex)+" · "+session.ranking.entries.get(result.entryIndex).name);}
                else{lastHit=-1;missAt=now();feedback(false);tell("Non è nella Top 10. Riprova!");}
                break;
        }
        input.setText("");
        lastSubmissionAt=now();
        if(session.phase()!=GameSession.Phase.PLAYING){
            hideKeyboard();finishedAt=now();
            if(blind)notice="";
            if(session.phase()==GameSession.Phase.FINISHED)record();else{pageDirection=1;pageAt=entered=now();}
            updateInput();requestLayout();
        }
        save();invalidate();
    }
    private void startSecondPlayer(){capturePage();pageDirection=1;session.startSecondPlayer();pageAt=entered=now();Arrays.fill(reveals,0);Arrays.fill(seals,0);finishedAt=0;input.setText("");updateInput();requestLayout();save();invalidate();}
    private void record(){if(!recorded){store.record(session);recorded=true;}}
    private String position(int index){return session.ranking.id.startsWith("skyscrapers")&&(index==7||index==8)?"8=":""+(index+1);}
    public void save(){android.content.SharedPreferences.Editor edit=store.prefs.edit();if(session!=null){edit.putString("session",session.serialize()).putString("ranking",session.ranking.id).putBoolean("recorded",recorded);}else{edit.remove("session").remove("ranking");}edit.apply();}
    private void restore(){String id=store.prefs.getString("ranking","");for(RankedList list:rankings)if(list.id.equals(id)){try{session=GameSession.restore(list,store.prefs.getString("session",""));recorded=store.prefs.getBoolean("recorded",false);}catch(RuntimeException ignored){session=null;}break;}}
    public void dispose(){updatePrompts.close();stopScroll();previousPage=null;shuffleGeneration++;queuedRanking=null;updater.close();if(tones!=null)tones.release();}
    public void goBack(){
        if(!modal.isEmpty()){closeSheet();}
        else if(screen.equals("onboarding")){if(onboardingStep>0)onboardingPage(onboardingStep-1);else if(onboardingReplay)finishOnboarding();else activity.finish();}
        else if(screen.equals("shuffle")){shuffleGeneration++;queuedRanking=null;show(shuffleReturn);}
        else if(screen.equals("game")){confirmExit();}
        else if(screen.equals("reveal")){show("results");}
        else if(screen.equals("duel")){duelRanking=null;show(dockPage(duelReturn)||(duelReturn.equals("results")&&session!=null)?duelReturn:"home");}
        else if(screen.equals("pick")){show(pendingMode==GameSession.Mode.DUEL?"duel":"home");}
        else if(!screen.equals("home")){show("home");}
        else activity.finish();
    }
    private void confirmExit(){hideKeyboard();dialog().setTitle("Una pausa dalla vetta?").setMessage("La partita viene salvata. Puoi riprenderla dalla home quando vuoi.").setNegativeButton("Continua",(d,w)->{}).setPositiveButton("Torna alla home",(d,w)->{save();show("home");}).show();}
    private void source(){RankedList r=session!=null&&!dockPage(screen)?session.ranking:detailRanking!=null?detailRanking:daily();dialog().setTitle("Dati veri, curiosità vere.").setMessage(r.sourceName+" · "+r.referenceYear+"\n\n"+r.methodology+"\n\n"+r.sourceUrl).setNegativeButton("Chiudi",null).setPositiveButton("Apri la fonte",(d,w)->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(r.sourceUrl)));}catch(Exception e){tell("Nessun browser disponibile.");}}).show();}

    @Override protected void onMeasure(int w,int h){
        int ww=MeasureSpec.getSize(w),hh=MeasureSpec.getSize(h);setMeasuredDimension(ww,hh);scale=ww/400f;H=hh/scale;keyboard=H<620;
        input.measure(exact(258),exact(52));submit.measure(exact(52),exact(52));nameOne.measure(exact(232),exact(44));nameTwo.measure(exact(232),exact(44));
    }
    private int exact(float size){return MeasureSpec.makeMeasureSpec((int)(size*scale),MeasureSpec.EXACTLY);}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        float y=H-84;
        place(input,42,y+6,300,y+58);place(submit,314,y+6,366,y+58);
        place(nameOne,118,228,350,272);place(nameTwo,118,360,350,404);
    }
    private void place(View view,float left,float top,float right,float bottom){view.layout((int)(left*scale),(int)(top*scale),(int)(right*scale),(int)(bottom*scale));}

    @Override protected void onDraw(Canvas raw){
        super.onDraw(raw);frameTime=now();readMotionPreference();frameAnimating=false;
        if(reduced()){previousPage=null;if(!scroller.isFinished())scroller.abortAnimation();}
        if(!modal.isEmpty()&&sheetClosing&&sheetProgress()<=0){modal="";sheetClosing=false;sheetDrag=0;updateInput();sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
        Canvas c=raw;c.save();c.scale(scale,scale);hits.clear();hitOffset=0;contentClip=false;alphaMul=1;
        backdrop(c);
        String summary=screen.equals("home")?"Topperia. Il mondo è una Top 10. Scegli come giocare.":screen.equals("library")?"Classifiche. Scegli una Top 10 da esplorare.":screen.equals("pick")?"Scegli una classifica. Modalità "+(pendingMode==GameSession.Mode.DUEL?"sfida uno contro uno":"solitaria"):screen.equals("profile")?"Il tuo percorso. Record "+store.number("best")+" su 10. "+store.number("rounds")+" partite.":screen.equals("onboarding")?"Benvenuto in Topperia. Introduzione, pagina "+(onboardingStep+1)+" di 3.":screen.equals("shuffle")?"Scelgo una classifica casuale per te.":screen.equals("duel")?"Sfida uno contro uno. Scrivete i nomi dei giocatori.":"Topperia";
        if(!summary.contentEquals(getContentDescription()==null?"":getContentDescription()))setContentDescription(summary);
        drawPageFrame(c);
        float nativeProgress=previousPage==null?1:Motion.ease(Motion.progress(now(),pageAt,380));
        float shake=shake();
        input.setTranslationX((pageShift+shake)*scale);submit.setTranslationX((pageShift+shake)*scale);
        nameOne.setTranslationX(pageShift*scale);nameTwo.setTranslationX(pageShift*scale);
        input.setAlpha(nativeProgress);submit.setAlpha(nativeProgress);nameOne.setAlpha(nativeProgress);nameTwo.setAlpha(nativeProgress);
        boolean ready=nativeProgress>.8f;input.setEnabled(ready);submit.setEnabled(ready);nameOne.setEnabled(ready);nameTwo.setEnabled(ready);
        nav(c);
        if(!modal.isEmpty())modal(c);
        toast(c);
        drawPress(c);
        c.restore();
        if(isShown()&&getWindowVisibility()==VISIBLE&&frameAnimating)postInvalidateOnAnimation();
    }

    /** Night sky: a vertical gradient with three slow drifting glows and a few twinkles. */
    private void backdrop(Canvas c){
        if(gradientHeight!=H){
            gradientHeight=H;backdropShader=new LinearGradient(0,0,0,H,BG_TOP,BG,Shader.TileMode.CLAMP);
            dockFade=new LinearGradient(0,H-170,0,H-70,BG&0xffffff,BG,Shader.TileMode.CLAMP);
            int[] colors={0x30dcf58d,0x24b39cf0,0x247fdcc0};blobs=new RadialGradient[3];
            for(int i=0;i<3;i++)blobs[i]=new RadialGradient(0,0,1,colors[i],colors[i]&0xffffff,Shader.TileMode.CLAMP);
        }
        p.setStyle(Paint.Style.FILL);p.setColor(0xff000000);p.setShader(backdropShader);c.drawRect(0,0,400,H,p);
        float t=anim();float[] bx={60,360,120},by={90,H*.5f,H*.92f},br={210,230,250};
        for(int i=0;i<3;i++){
            float x=bx[i]+(float)Math.sin(t*.21f+i*2.1f)*36,y=by[i]+(float)Math.cos(t*.17f+i*1.3f)*44,r=br[i]*(1+.08f*(float)Math.sin(t*.37f+i));
            shaderMatrix.setScale(r,r);shaderMatrix.postTranslate(x,y);blobs[i].setLocalMatrix(shaderMatrix);p.setShader(blobs[i]);c.drawCircle(x,y,r,p);
        }
        p.setShader(null);
        for(int i=0;i<18;i++){float x=(i*97+31)%380+10,y=((i*173+57)%997)/997f*H;float twinkle=.5f+.5f*(float)Math.sin(t*1.1f+i*1.7f);circle(c,x,y,.6f+twinkle*.9f,(Math.round(80*twinkle)<<24)|0xffffff);}
        if(!reduced())frameAnimating=true;
    }

    private void drawPageFrame(Canvas c){
        float t=reduced()||previousPage==null?1:Motion.ease(Motion.progress(now(),pageAt,380));
        pageShift=previousPage==null?0:pageDirection*28*(1-t);
        float before=alphaMul;alphaMul=before*t;
        c.save();c.translate(pageShift,0);drawPage(c);c.restore();alphaMul=before;
        if(previousPage!=null&&t<1){
            int layer=c.saveLayerAlpha(0,0,400,H,(int)(255*(1-t)));
            c.translate(-pageDirection*18*t,0);c.drawPicture(previousPage);c.restoreToCount(layer);frameAnimating=true;
        }else if(!capturing)previousPage=null;
    }
    private void drawPage(Canvas c){clipBottom=H;switch(screen){case "onboarding":onboarding(c);break;case "shuffle":shuffle(c);break;case "home":home(c);break;case "library":catalog(c,false);break;case "pick":catalog(c,true);break;case "profile":profile(c);break;case "duel":duelSetup(c);break;case "game":game(c);break;case "reveal":reveal(c);break;case "results":results(c);break;}}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);previousPage=null;stopScroll();}
    @Override protected void onDetachedFromWindow(){stopScroll();previousPage=null;super.onDetachedFromWindow();}
    @Override public void computeScroll(){if(scroller.computeScrollOffset()){scroll=Math.max(0,Math.min(maxScroll,scroller.getCurrY()/scale));postInvalidateOnAnimation();}}

    private void stopScroll(){scroller.abortAnimation();if(velocity!=null){velocity.recycle();velocity=null;}activePointer=-1;scrollingGesture=false;}
    private float sheetProgress(){return reduced()?sheetTarget:Motion.lerp(sheetFrom,sheetTarget,Motion.ease(Motion.progress(now(),sheetAt,sheetClosing?240:380)));}
    private float sheetHeight(){return Math.min(H-24,modal.equals("settings")?592:modal.equals("rules")?548:492);}
    private float sheetTop(){return Math.max(24,H-sheetHeight())+(1-sheetProgress())*sheetHeight()+sheetDrag;}
    private void openSheet(String name){readMotionPreference();stopScroll();clearPress();modal=name;sheetFrom=0;sheetTarget=1;sheetAt=now();sheetDrag=0;sheetClosing=false;hits.clear();accessibilityFocus=hovered=-1;hideKeyboard();updateInput();invalidate();sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
    private void closeSheet(){if(modal.isEmpty()||sheetClosing)return;sheetFrom=Math.max(0,sheetProgress()-sheetDrag/sheetHeight());sheetTarget=0;sheetAt=now();sheetClosing=true;sheetDrag=0;clearPress();invalidate();}
    private void clearPress(){pressedBounds=null;pressRelease=1;}
    private void releasePress(){if(pressedBounds!=null){pressAt=now();pressRelease=0;invalidate();}}
    private void drawPress(Canvas c){
        if(pressedBounds==null||reduced())return;
        float t=pressRelease==0?Motion.progress(now(),pressAt,240):0;
        if(pressRelease==0&&t>=1){clearPress();return;}
                if(pressRelease==0||now()-pressAt<100)frameAnimating=true;
    }
    private float shake(){if(reduced()||missAt==0)return 0;float t=Motion.progress(now(),missAt,440);if(t>=1)return 0;frameAnimating=true;return (float)Math.sin(t*Math.PI*7)*(1-t)*9;}

    // Staggered entrances: each block rises and fades in shortly after the previous one.
    private float enterT(int order){if(reduced())return 1;float t=Motion.ease(Motion.progress(frameTime,pageAt+order*55L,480));if(t<1)frameAnimating=true;return t;}
    private float push(Canvas c,int order){return push(c,enterT(order));}
    private float push(Canvas c,float t){float before=alphaMul;c.save();c.translate(0,(1-t)*24);alphaMul=before*t;return before;}
    private void pop(Canvas c,float before){c.restore();alphaMul=before;}

    private void pageStart(Canvas c){clipBottom=dockPage(screen)?H-DOCK_SPACE:(screen.equals("results")||screen.equals("reveal"))?H-96:H;c.save();c.translate(0,-scroll);hitOffset=-scroll;contentClip=true;}
    private void pageEnd(Canvas c,float height){c.restore();hitOffset=0;contentClip=false;maxScroll=Math.max(0,height-clipBottom);scroll=Math.min(scroll,maxScroll);}
    private void topBar(Canvas c){brand(c,24,44,.9f);roundButton(c,"settings",352,36,"Impostazioni",()->openSheet("settings"));}
    private void roundButton(Canvas c,String symbol,float cx,float cy,String label,Runnable action){float sink=3.5f*pressAmount(cx-26,cy-26,52,52);circle(c,cx,cy+7,22,0x50000000);circle(c,cx,cy+3.5f,22,darken(CARD_HI,.55f));orb(c,cx,cy+sink,22,CARD_HI);ringStroke(c,cx,cy+sink,22,LINE,1);icon(c,symbol,cx-10,cy-10+sink,20,TEXT);hit(cx-26,cy-26,52,52,label,action);}
    private String greeting(){int hour=java.time.LocalTime.now(java.time.ZoneId.of("Europe/Rome")).getHour();return hour<5?"NOTTE DA CURIOSI":hour<12?"BUONGIORNO, MENTE CURIOSA":hour<18?"BUON POMERIGGIO, MENTE CURIOSA":"BUONASERA, MENTE CURIOSA";}
    private int accent(String category){int index=categories.indexOf(category);return ACCENTS[Math.floorMod(index-1,ACCENTS.length)];}
    private int playerColor(int index){return index==0?LIME:LILAC;}
    private int playedCount(){int played=0;for(RankedList r:rankings)if(store.option("played_"+r.id,false))played++;return played;}

    // ——— Onboarding ———
    private void onboarding(Canvas c){
        if(!reduced())frameAnimating=true;
        maxScroll=0;brand(c,24,44,.9f);pill(c,onboardingReplay?"Chiudi":"Salta",304,18,72,36,CARD_HI,TEXT,12);hit(298,10,84,52,"Salta introduzione",this::finishOnboarding);
        for(int i=0;i<3;i++){float w=i==onboardingStep?44:16;float x=24;for(int j=0;j<i;j++)x+=(j==onboardingStep?44:16)+6;rect(c,x,76,w,5,2.5f,i<=onboardingStep?LIME:CARD_HI);}
        float artHeight=Math.min(300,Math.max(170,H*.34f)),artY=104+artHeight/2;
        float contentScale=artHeight/300;
        c.save();c.translate(200,artY);c.scale(contentScale,contentScale);
        float sway=(float)Math.sin(anim()*1.5f)*5;
        glow(c,0,0,170,onboardingStep==1?0x50c4b2f7:0x48dcf58d);
        if(onboardingStep==0){
            circle(c,0,5,132,0x14ffffff);
            for(int i=0;i<8;i++){double a=i*Math.PI/4+anim()*.4;float rr=150+(float)Math.sin(anim()*2+i)*6;star(c,(float)Math.cos(a)*rr,(float)Math.sin(a)*rr*.82f,i%2==0?6:4,ACCENTS[i%5]);}
            c.save();c.translate(0,sway-4);c.rotate((float)Math.sin(anim()*.7f)*3);rotate3d(c,0,0,(float)Math.sin(anim()*.9f)*7,(float)Math.sin(anim()*.6f)*16);logoMark(c,-150,-150,300);c.restore();
            pill(c,"10 POSTI. UN MONDO INTERO.",-118,124,236,30,LIME,INK,9.5f);
        }else if(onboardingStep==1){
            for(int i=0;i<3;i++){c.save();c.rotate((i-1)*7);rect(c,-105,-95+i*11,210,93,20,i==2?CARD_HI:0x33c4b2f7);c.restore();}
            float cycle=reduced()?3.4f:(now()-entered)%5200/1000f;
            float turn=reduced()?1:Math.min(1,Math.max(0,(cycle-1.65f)/.55f));boolean solved=turn>=.5f;
            c.save();flip3d(c,0,-25,turn,true);rect(c,-111,-73,222,104,21,solved?darken(TEAL,.5f):darken(CARD_HI,.55f));if(solved)gradRect(c,-111,-77,222,104,21,LIME,TEAL);else rect(c,-111,-77,222,104,21,CARD_HI);circle(c,-79,-25,18,solved?INK:0x22ffffff);center(c,solved?"1":"?",-79,-19,18,solved?LIME:MUTED,bold);
            text(c,solved?"Repubblica Ceca":"Chi c’è lassù?",-49,-30,16,solved?INK:TEXT,bold);text(c,solved?"148,8 litri per abitante":"Una casella da scoprire",-49,-7,10,solved?0xaa0c2019:MUTED,regular);c.restore();
            c.save();c.translate(0,sway);rect(c,-127,64,254,60,22,CARD_HI);ringRect(c,-127,64,254,60,22,0x66dcf58d);icon(c,"spark",-111,82,22,LIME);String written=cycle<.5f?"La tua idea…":cycle<1?"Ce":cycle<1.4f?"Cech":"Cechia";text(c,written,-77,101,19,TEXT,bold);circle(c,99,94,20,LIME);center(c,solved?"✓":"↑",99,101,21,INK,bold);c.restore();
            if(solved){star(c,124,-81,10,LIME);star(c,-126,-53,6,ORANGE);pill(c,"DENTRO!",-47,-121,94,26,LIME,INK,10);}
        }else{
            float k=(float)(1-Math.cos(anim()*1.6))/2;
            avatar(c,-92,-6+sway,40,"A",LIME);avatar(c,92,-6-sway,40,"B",LILAC);
            for(int i=0;i<3;i++){c.save();c.translate(-92+(i-1)*28,74);c.rotate((i-1)*10);envelope(c,LIME);c.restore();c.save();c.translate(92+(i-1)*28,74);c.rotate((i-1)*10);envelope(c,LILAC);c.restore();}
            c.save();c.translate(0,-6);c.rotate(-8+16*k);vsBadge(c,0,0,30);c.restore();
            star(c,-14,-112,10,ORANGE);star(c,124,-80,7,LIME);star(c,-128,-70,6,PINK);
        }
        c.restore();
        float textY=104+artHeight+46;
        String[] first={"Il mondo ha","Un’idea. Un posto","Sfida un amico,"},second={"dieci posti.","in classifica.","alla cieca."};
        text(c,first[onboardingStep],24,textY,33,TEXT,heavy);text(c,second[onboardingStep],24,textY+39,33,onboardingStep==2?LILAC:LIME,heavy);
        String[] detail={"Ogni Top 10 nasconde risposte reali. A te il piacere di scoprirle.","Hai 10 tentativi. Scrivi una risposta: se c’è, scopri subito il suo posto.","Stesso telefono, stessa classifica. Le risposte restano sigillate fino alla resa dei conti finale."};
        wrap(c,detail[onboardingStep],25,textY+76,350,15,22,MUTED,regular,3);
        read(24,textY-30,352,160,first[onboardingStep]+" "+second[onboardingStep]+" "+detail[onboardingStep]);
        float footerY=H-86;
        if(onboardingStep>0){circle(c,51,footerY+28,27,CARD_HI);icon(c,"back",41,footerY+18,20,TEXT);hit(24,footerY,54,56,"Pagina precedente",()->onboardingPage(onboardingStep-1));}
        button(c,onboardingStep==2?"Esplora Topperia  →":"Continua  →",onboardingStep>0?92:24,footerY,onboardingStep>0?284:352,56,LIME,INK,()->onboardingPage(onboardingStep+1));
        center(c,onboardingStep==2?"LA PROSSIMA SCOPERTA È TUA":"Scorri per continuare",200,H-14,9,DIM,bold);
    }
    private void envelope(Canvas c,int color){rect(c,-18,-13,36,26,6,color);line(c,-16,-11,0,2,INK,1.6f);line(c,16,-11,0,2,INK,1.6f);}

    private void dice(Canvas c,float x,float y,float size,int bg,int fg,int face){
        rect(c,x,y+size*.06f,size,size,size*.25f,0x33000000);rect(c,x,y,size,size,size*.25f,bg);
        float r=size*.065f,a=size*.29f,b=size*.71f,m=size*.5f;
        if(face%2==1)circle(c,x+m,y+m,r,fg);
        if(face>=2){circle(c,x+a,y+a,r,fg);circle(c,x+b,y+b,r,fg);}
        if(face>=4){circle(c,x+a,y+b,r,fg);circle(c,x+b,y+a,r,fg);}
        if(face==6){circle(c,x+a,y+m,r,fg);circle(c,x+b,y+m,r,fg);}
    }

    // ——— Shuffle: cards orbit, then one flips to reveal the chosen Top 10 ———
    private void shuffle(Canvas c){
        if(!reduced())frameAnimating=true;
        maxScroll=0;roundButton(c,"back",46,36,"Annulla scelta casuale",this::goBack);brand(c,150,44,.82f);
        float cy=H*.42f;long elapsed=now()-entered;float t=reduced()?1:Math.min(1,elapsed/2100f);
        glow(c,200,cy,190,0x44dcf58d);
        RankedList chosen=queuedRanking;
        if(t<.6f&&!reduced()){
            float spin=elapsed*.32f,spread=Motion.ease(Math.min(1,t/.2f))*(1-Motion.ease(Math.max(0,(t-.4f)/.2f)))*86;
            for(int i=0;i<6;i++){c.save();c.translate(200,cy);c.rotate(i*60+spin);c.translate(0,-spread);c.rotate(-(i*60+spin)*.5f);rotate3d(c,0,0,(float)Math.sin(elapsed/260f+i)*28,(float)Math.cos(elapsed/300f+i)*34);rect(c,-34,-46,68,92,14,0x40000000);rect(c,-32,-48,64,92,14,ACCENTS[i%5]);center(c,"?",0,-2,26,INK,heavy);c.restore();}
        }else{
            float k=reduced()?1:Motion.ease((t-.6f)/.4f);float w=64+(272-64)*k,h=92+(176-92)*k;
            boolean front=reduced()||k>.5f;
            c.save();c.translate(200,cy);flip3d(c,0,0,reduced()?1:k,false);
            rect(c,-w/2,-h/2+6,w,h,22,0x55000000);
            if(front&&chosen!=null){
                rect(c,-w/2,-h/2,w,h,22,0xfffbfdf3);iconBadge(c,chosen.icon,-w/2+18,-h/2+18,46,accent(chosen.category));
                text(c,chosen.category.toUpperCase(Locale.ITALIAN),-w/2+76,-h/2+46,9.5f,DIM,bold);
                wrap(c,chosen.title,-w/2+18,-h/2+96,w-36,17,22,INK,heavy,3);
            }else{rect(c,-w/2,-h/2,w,h,22,LIME);center(c,"?",0,12,34,INK,heavy);}
            c.restore();
            if(front)for(int i=0;i<9;i++){double a=i*Math.PI*2/9;float d=150+Motion.ease(Math.min(1,(t-.8f)*5))*30;star(c,200+(float)Math.cos(a)*d,cy+(float)Math.sin(a)*d*.7f,4+i%3*2,ACCENTS[i%5]);}
        }
        boolean revealed=t>=.8f||reduced();
        center(c,revealed?"Ecco la tua Top 10!":"La curiosità decide…",200,cy+178,27,TEXT,heavy);center(c,revealed?"Preparati a scalare la classifica.":"Mescolo le classifiche per te.",200,cy+208,14,MUTED,regular);
        for(int i=0;i<3;i++)circle(c,184+i*16,cy+242,3.5f,reduced()||((now()-entered)/220)%3==i?LIME:CARD_HI);
    }

    // ——— Home ———
    private void home(Canvas c){
        pageStart(c);
        float o=push(c,0);topBar(c);pop(c,o);
        o=push(c,1);
        text(c,greeting(),24,98,10.5f,LIME,bold);
        text(c,"Il mondo è",22,142,40,TEXT,heavy);text(c,"una",22,188,40,TEXT,heavy);text(c,"Top 10.",22+measure("una ",40,heavy),188,40,LIME,heavy);
        pop(c,o);
        float y=214;
        if(session!=null&&session.phase()!=GameSession.Phase.FINISHED){o=push(c,2);resumeCard(c,24,y,352,84);pop(c,o);y+=100;}
        o=push(c,3);surprise(c,24,y,352,198);pop(c,o);y+=222;
        o=push(c,4);text(c,"Come vuoi giocare?",24,y+6,20,TEXT,bold);pop(c,o);y+=24;
        o=push(c,5);modeTile(c,24,y,171,206,LIME,"solo","Da solo","Tu contro la classifica, con 10 tentativi.",()->choose(GameSession.Mode.SOLO));pop(c,o);
        o=push(c,6);modeTile(c,205,y,171,206,LILAC,"duel","1 contro 1","Due giocatori, risposte alla cieca.",()->choose(GameSession.Mode.DUEL));pop(c,o);
        y+=230;
        RankedList day=daily();
        o=push(c,7);text(c,"CURIOSITÀ DEL GIORNO",24,y,10,MUTED,bold);rankingRow(c,24,y+14,352,94,day,()->openDetail(day));pop(c,o);y+=130;
        if(session!=null&&session.phase()==GameSession.Phase.FINISHED){o=push(c,8);lastResult(c,24,y,352,72);pop(c,o);y+=88;}
        o=push(c,9);pill(c,"COME SI GIOCA",24,y,150,40,CARD_HI,TEXT,10.5f);hit(24,y,150,40,"Come si gioca",()->openSheet("rules"));pop(c,o);
        pageEnd(c,y+60);
    }
    private void resumeCard(Canvas c,float x,float y,float w,float h){
        pressTransform(c,x,y,w,h);
        card(c,x,y,w,h,24);c.save();clipRound(c,x,y,w,h,24);glow(c,x+40,y+h/2,90,0x40dcf58d);c.restore();ringRect(c,x,y,w,h,24,0x66dcf58d);
        GameSession.PlayerState player=session.currentPlayer();boolean handoff=session.phase()==GameSession.Phase.HANDOFF;
        ring(c,x+42,y+h/2,21,5,player.attemptsUsed()/10f,session.isBlind()?playerColor(session.activePlayerIndex()):LIME,CARD_HI);
        center(c,""+player.attemptsUsed(),x+42,y+h/2+6,15,TEXT,heavy);
        text(c,handoff?"SFIDA IN PAUSA":"RIPRENDI LA PARTITA",x+78,y+28,9.5f,LIME,bold);
        fit(c,session.ranking.title,x+78,y+50,w-140,15,TEXT,bold);
        fit(c,session.isBlind()?(handoff?"Tocca a "+session.player(1).name:"Turno di "+player.name+" · alla cieca"):player.score()+" scoperte · "+player.attemptsRemaining()+" tentativi",x+78,y+69,w-140,11.5f,MUTED,regular);
        circle(c,x+w-34,y+h/2,20,LIME);icon(c,"play",x+w-43,y+h/2-9,18,INK);
        c.restore();hit(x,y,w,h,"Riprendi partita",()->show("game"));
    }
    private void surprise(Canvas c,float x,float y,float w,float h){
        pressTransform(c,x,y,w,h);
        rect(c,x,y+6,w,h,28,0x55000000);gradRect(c,x,y,w,h,28,LIME,TEAL);
        c.save();clipRound(c,x,y,w,h,28);
        circle(c,x+w-60,y+20,90,0x22ffffff);circle(c,x+w-20,y+h,70,0x1a0c2019);
        pill(c,"LASCIA FARE AL CASO",x+18,y+18,148,24,0x260c2019,INK,8.5f);
        text(c,"Una Top 10",x+20,y+82,27,INK,heavy);text(c,"a sorpresa.",x+20,y+114,27,INK,heavy);
        text(c,"Il dado sceglie, tu indovini.",x+20,y+138,12,0xbb0c2019,regular);
        rect(c,x+18,y+h-50,146,36,18,INK);icon(c,"dice",x+32,y+h-41,18,LIME);text(c,"Sorprendimi",x+58,y+h-27,13,LIME,bold);
        fan(c,x+w-74,y+h/2+6);
        c.restore();c.restore();
        hit(x,y,w,h,"Sorprendimi: gioca una classifica casuale in solitaria",()->{pendingMode=GameSession.Mode.SOLO;randomRound("Tutte");});
    }
    private void fan(Canvas c,float cx,float cy){
        float t=anim(),sway=(float)Math.sin(t*1.3f)*5;
        c.save();c.translate(cx,cy);rotate3d(c,0,0,(float)Math.sin(t*.9f)*10,(float)Math.sin(t*.7f)*24);
        c.save();c.rotate(-22+sway*.6f);rect(c,-34,-50,68,96,14,0x330c2019);rect(c,-36,-54,68,96,14,LILAC);c.restore();
        c.save();c.rotate(-7+sway*.3f);rect(c,-36,-54,68,96,14,ORANGE);center(c,"?",-2,4,24,INK,heavy);c.restore();
        c.save();c.rotate(10+sway);rect(c,-36,-54,68,96,14,0xfffbfdf3);center(c,"#1",-2,-10,25,INK,heavy);rect(c,-20,6,36,6,3,0x330c2019);rect(c,-20,18,24,6,3,0x220c2019);c.restore();
        float bob=-Math.abs((float)Math.sin(t*2.2f))*10;c.save();c.translate(-46,48+bob);c.rotate((float)Math.sin(t*1.6f)*18);dice(c,-16,-16,32,INK,LIME,5);c.restore();
        star(c,40,-64,7,INK);star(c,52,40,4,0xff0c2019);
        c.restore();if(!reduced())frameAnimating=true;
    }
    private void modeTile(Canvas c,float x,float y,float w,float h,int color,String symbol,String title,String subtitle,Runnable action){
        pressTransform(c,x,y,w,h);
        card(c,x,y,w,h,26);
        c.save();clipRound(c,x,y,w,h,26);glow(c,x+w-16,y+12,130,(color&0xffffff)|0x3a000000);c.restore();
        float bob=(float)Math.sin(anim()*1.4f+(color==LIME?0:1.5f))*3;
        circle(c,x+46,y+60,30,0x50000000);circle(c,x+46,y+56+bob,30,darken(color,.55f));orb(c,x+46,y+52+bob,30,color);icon(c,symbol,x+32,y+38+bob,28,INK);
        text(c,title,x+18,y+122,21,TEXT,heavy);
        wrap(c,subtitle,x+18,y+146,w-36,12,17,MUTED,regular,3);
        circle(c,x+w-32,y+h-30,16,(color&0xffffff)|0x33000000);icon(c,"next",x+w-40,y+h-38,16,color);
        c.restore();hit(x,y,w,h,title,action);
    }
    private void rankingRow(Canvas c,float x,float y,float w,float h,RankedList r,Runnable action){
        pressTransform(c,x,y,w,h);
        card(c,x,y,w,h,22);int accent=accent(r.category);
        iconBadge(c,r.icon,x+16,y+(h-52)/2,52,accent);
        wrap(c,r.title,x+82,y+36,w-128,15,20,TEXT,bold,2);
        fit(c,r.category+"  ·  "+r.referenceYear+(store.option("played_"+r.id,false)?"  ·  giocata":""),x+82,y+h-16,w-128,11,MUTED,regular);
        circle(c,x+w-28,y+h/2,15,CARD_HI);icon(c,"next",x+w-35,y+h/2-7,14,TEXT);
        c.restore();hit(x,y,w,h,"Classifica: "+r.title,action);
    }
    private void lastResult(Canvas c,float x,float y,float w,float h){
        pressTransform(c,x,y,w,h);card(c,x,y,w,h,22);
        circle(c,x+38,y+h/2,20,0x33f6b38a);icon(c,"trophy",x+27,y+h/2-11,22,ORANGE);
        text(c,"RIVEDI L’ULTIMA PARTITA",x+70,y+30,9.5f,ORANGE,bold);fit(c,session.ranking.title,x+70,y+51,w-120,14,TEXT,bold);
        icon(c,"next",x+w-36,y+h/2-8,16,MUTED);
        c.restore();hit(x,y,w,h,"Rivedi l’ultima partita",()->show("results"));
    }

    // ——— Catalogue: browsing (dock tab) or picking for a chosen mode ———
    private void catalog(Canvas c,boolean picking){
        pageStart(c);
        float o=push(c,0);
        if(picking){roundButton(c,"back",46,36,"Indietro",this::goBack);center(c,"Scegli la Top 10",200,42,15,TEXT,bold);}else topBar(c);
        pop(c,o);
        o=push(c,1);float y;
        if(picking){
            boolean duel=pendingMode==GameSession.Mode.DUEL;
            text(c,duel?"SFIDA 1 CONTRO 1 · ALLA CIECA":"IN SOLITARIA",24,98,10.5f,duel?LILAC:LIME,bold);
            if(duel)fit(c,store.prefs.getString("player1","Giocatore 1")+" vs "+store.prefs.getString("player2","Giocatore 2"),22,138,356,30,TEXT,heavy);
            else text(c,"Cosa scopri oggi?",22,138,30,TEXT,heavy);
            y=164;
        }else{
            int played=playedCount();
            text(c,"CATALOGO",24,98,10.5f,LIME,bold);text(c,"Classifiche",22,142,36,TEXT,heavy);
            text(c,played+" di "+rankings.size()+" esplorate",24,168,12,MUTED,regular);
            rect(c,24,180,352,6,3,CARD_HI);if(played>0)gradRect(c,24,180,352f*played/rankings.size(),6,3,LIME,TEAL);
            y=204;
        }
        pop(c,o);
        o=push(c,2);y=chips(c,y);pop(c,o);
        if(picking){o=push(c,3);surpriseStrip(c,24,y,352,76);pop(c,o);y+=94;}
        ArrayList<RankedList> visible=new ArrayList<>();for(RankedList r:rankings)if(filter.equals("Tutte")||r.category.equals(filter))visible.add(r);
        o=push(c,picking?4:3);text(c,String.format(Locale.ITALIAN,"%d CLASSIFICHE · 10 TENTATIVI CIASCUNA",visible.size()),24,y+4,9.5f,MUTED,bold);pop(c,o);y+=18;
        int drawn=0;
        for(int i=0;i<visible.size();i++){
            float x=24+(i%2)*181,cy=y+(i/2)*(CARD_H+10);
            // Skip all row work until its bounds actually enter the viewport.
            if(cy+CARD_H<scroll||cy-scroll>H)continue;
            float t=reduced()?1:Motion.ease(Motion.progress(now(),Math.max(filterAt,pageAt)+120+Math.min(drawn++,6)*45L,420));
            if(t<1)frameAnimating=true;
            float before=push(c,t);hitOffset+=(1-t)*24;
            rankingCard(c,x,cy,171,CARD_H,visible.get(i),picking);
            hitOffset-=(1-t)*24;pop(c,before);
        }
        y+=((visible.size()+1)/2)*(CARD_H+10);
        text(c,"Ogni classifica racconta dati di un anno preciso.",24,y+10,10,DIM,regular);
        pageEnd(c,y+34);
    }
    private float chips(Canvas c,float y){
        float cx=24;
        for(String category:categories){
            float w=measure(category,11,bold)+28;if(cx+w>377){cx=24;y+=40;}
            boolean selected=filter.equals(category);
            float sink=3*pressAmount(cx,y,w,32);int chip=selected?LIME:CARD_HI;rect(c,cx,y+3,w,32,16,darken(chip,.5f));pill(c,category,cx,y+sink,w,32,chip,selected?INK:MUTED,11);if(selected)rect(c,cx+8,y+sink+2,w-16,10,5,0x40ffffff);
            final String choice=category;
            hit(cx,y,w,32,"Categoria "+category,()->{if(!filter.equals(choice)){stopScroll();filter=choice;scroll=0;filterAt=now();accessibilityFocus=hovered=-1;invalidate();sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}});
            cx+=w+7;
        }
        return y+52;
    }
    private void surpriseStrip(Canvas c,float x,float y,float w,float h){
        pressTransform(c,x,y,w,h);
        gradRect(c,x,y,w,h,22,LIME,TEAL);
        text(c,"Sorprendimi",x+20,y+32,19,INK,heavy);fit(c,filter.equals("Tutte")?"Una Top 10 a caso. Il resto lo fai tu.":"Una sorpresa tra: "+filter+".",x+20,y+54,w-100,11.5f,0xcc0c2019,regular);
        c.save();c.translate(x+w-42,y+h/2);c.rotate((float)Math.sin(anim()*1.4f)*9);dice(c,-20,-20,40,INK,LIME,5);c.restore();if(!reduced())frameAnimating=true;
        c.restore();hit(x,y,w,h,"Classifica casuale: "+filter,()->randomRound(filter));
    }
    private void rankingCard(Canvas c,float x,float y,float w,float h,RankedList r,boolean picking){
        pressTransform(c,x,y,w,h);
        int accent=accent(r.category);
        card(c,x,y,w,h,22);
        c.save();clipRound(c,x,y,w,h,22);glow(c,x+36,y+36,96,(accent&0xffffff)|0x2a000000);c.restore();
        iconBadge(c,r.icon,x+14,y+14,46,accent);
        if(store.option("played_"+r.id,false)){circle(c,x+w-24,y+28,11,LIME);icon(c,"check",x+w-31,y+21,14,INK);}
        fit(c,r.category.toUpperCase(Locale.ITALIAN),x+16,y+84,w-32,8.5f,accent,bold);
        wrap(c,r.title,x+16,y+106,w-30,14,18.5f,TEXT,bold,3);
        text(c,r.referenceYear,x+16,y+h-16,10.5f,MUTED,bold);
        c.restore();
        hit(x,y,w,h,(picking?"Gioca: ":"Classifica: ")+r.title,picking?()->start(r):()->openDetail(r));
    }

    // ——— Profile ———
    private static final String[] LEVELS={"Curioso","Apprendista","Esploratore","Scalatore","Esperto","Maestro","Leggenda"};
    private void profile(Canvas c){
        pageStart(c);
        float o=push(c,0);topBar(c);pop(c,o);
        o=push(c,1);text(c,"IL TUO PERCORSO",24,98,10.5f,LIME,bold);text(c,"Ciao, mente curiosa.",22,138,30,TEXT,heavy);pop(c,o);
        int hitsTotal=store.number("hits"),level=1,need=10,reached=0;while(hitsTotal>=reached+need){reached+=need;level++;need+=5;}
        float y=158;
        o=push(c,2);
        rect(c,24,y+6,352,164,28,0x55000000);gradRect(c,24,y,352,164,28,LILAC,PINK);
        c.save();clipRound(c,24,y,352,164,28);circle(c,330,y+20,80,0x22ffffff);c.save();c.translate(310,y+92);c.scale(.82f,.82f);trophy(c,0,0,LIME);c.restore();c.restore();
        pill(c,"LIVELLO "+level,44,y+20,92,26,0x260c2019,INK,10);
        text(c,LEVELS[Math.min(level-1,LEVELS.length-1)],44,y+80,30,INK,heavy);
        text(c,hitsTotal+" scoperte in tutto",44,y+104,12.5f,0xcc0c2019,regular);
        float progress=(hitsTotal-reached)/(float)need;
        rect(c,44,y+122,220,8,4,0x260c2019);rect(c,44,y+122,Math.max(8,220*progress),8,4,INK);
        text(c,"Ancora "+(reached+need-hitsTotal)+" per il livello "+(level+1),44,y+148,10.5f,0xcc0c2019,bold);
        read(24,y,352,164,"Livello "+level+", "+LEVELS[Math.min(level-1,LEVELS.length-1)]+". "+hitsTotal+" scoperte in tutto.");
        pop(c,o);y+=182;
        int tries=store.number("attempts");
        o=push(c,3);
        stat(c,24,y,"trophy",LIME,store.number("best")+"/10","RECORD");stat(c,205,y,"flag",LILAC,""+store.number("rounds"),"PARTITE");
        stat(c,24,y+104,"star",ORANGE,""+store.number("hits"),"SCOPERTE");stat(c,205,y+104,"target",TEAL,tries==0?"—":Math.round(store.number("hits")*100f/tries)+"%","PRECISIONE");
        pop(c,o);y+=214;
        int played=playedCount();
        o=push(c,4);card(c,24,y,352,78,22);text(c,"Classifiche esplorate",44,y+32,14,TEXT,bold);textRight(c,played+" / "+rankings.size(),356,y+32,14,LIME,heavy);
        rect(c,44,y+48,312,8,4,CARD_HI);if(played>0)gradRect(c,44,y+48,312f*played/rankings.size(),8,4,LIME,TEAL);pop(c,o);y+=104;
        o=push(c,5);text(c,"Traguardi",24,y,20,TEXT,heavy);pop(c,o);y+=16;
        String[][] goals={{"La prima vetta","Completa la tua prima partita","flag"},{"A metà del cielo","Trova almeno 5 risposte in un round","star"},{"Cima perfetta","Scopri una Top 10 completa","trophy"},{"Due menti, una sfida","Completa una partita 1 contro 1","duel"},{"Curioso seriale","Gioca 10 partite","spark"},{"Collezionista","Esplora 10 classifiche diverse","grid"}};
        boolean[] unlocked={store.number("rounds")>=1,store.number("best")>=5,store.number("best")==10,store.number("duels")>=1,store.number("rounds")>=10,played>=10};
        for(int i=0;i<goals.length;i++){o=push(c,6+i);achievement(c,y,goals[i][0],goals[i][1],unlocked[i],goals[i][2],ACCENTS[i%5]);pop(c,o);y+=74;}
        y+=12;
        o=push(c,12);button(c,"Impostazioni",24,y,171,48,CARD_HI,TEXT,()->openSheet("settings"));button(c,"Come si gioca",205,y,171,48,CARD_HI,TEXT,()->openSheet("rules"));pop(c,o);
        pageEnd(c,y+70);
    }
    private void stat(Canvas c,float x,float y,String symbol,int color,String number,String label){card(c,x,y,171,92,22);circle(c,x+30,y+30,16,(color&0xffffff)|0x30000000);icon(c,symbol,x+21,y+21,18,color);text(c,number,x+18,y+76,26,TEXT,heavy);textRight(c,label,x+155,y+35,9,MUTED,bold);read(x,y,171,92,label+": "+number);}
    private void achievement(Canvas c,float y,String title,String detail,boolean unlocked,String symbol,int color){
        card(c,24,y,352,64,20);
        circle(c,56,y+32,20,unlocked?color:CARD_HI);icon(c,symbol,46,y+22,20,unlocked?INK:DIM);
        text(c,title,88,y+28,14.5f,unlocked?TEXT:MUTED,bold);fit(c,detail,88,y+47,236,11,unlocked?MUTED:DIM,regular);
        if(unlocked){circle(c,350,y+32,12,LIME);icon(c,"check",343,y+25,14,INK);}else icon(c,"lock",341,y+23,18,DIM);
        read(24,y,352,64,title+". "+detail+(unlocked?". Sbloccato.":". Da sbloccare."));
    }

    // ——— Duel setup: names, then a ranking ———
    private void duelSetup(Canvas c){
        maxScroll=0;scroll=0;
        float o=push(c,0);roundButton(c,"back",46,36,"Indietro",this::goBack);center(c,"Sfida 1 contro 1",200,42,15,TEXT,bold);pop(c,o);
        o=push(c,1);
        text(c,"NUOVA SFIDA · ALLA CIECA",24,98,10.5f,LILAC,bold);text(c,"Chi sfida chi?",22,138,32,TEXT,heavy);
        if(duelRanking!=null)fit(c,"Classifica: "+duelRanking.title,24,164,352,12.5f,MUTED,regular);else text(c,"Stesso telefono, stessa classifica.",24,164,13,MUTED,regular);
        pop(c,o);
        o=push(c,2);playerCard(c,178,1,nameOne,LIME);pop(c,o);
        o=push(c,3);playerCard(c,310,2,nameTwo,LILAC);pop(c,o);
        o=push(c,4);c.save();c.translate(200,300);c.rotate(reduced()?0:(float)Math.sin(anim()*1.3f)*8);vsBadge(c,0,0,24);c.restore();pop(c,o);
        if(!keyboard){
            o=push(c,5);float y=444;card(c,24,y,352,138,24);
            String[][] rows={{"target","Ognuno ha 10 tentativi sulla stessa Top 10."},{"lock","Nessun indizio: le risposte restano sigillate."},{"trophy","Alla fine, la resa dei conti!"}};
            for(int i=0;i<3;i++){circle(c,52,y+30+i*40,14,(ACCENTS[(i+1)%5]&0xffffff)|0x33000000);icon(c,rows[i][0],44,y+22+i*40,16,ACCENTS[(i+1)%5]);fit(c,rows[i][1],76,y+35+i*40,284,12.5f,TEXT,regular);}
            read(24,y,352,138,rows[0][1]+" "+rows[1][1]+" "+rows[2][1]);
            pop(c,o);
        }
        if(H-82>440){o=push(c,6);button(c,duelRanking==null?"Scegli la classifica  →":"Inizia la sfida  →",24,H-82,352,58,LILAC,INK,this::duelContinue);pop(c,o);}
    }
    private void playerCard(Canvas c,float y,int number,EditText field,int color){
        card(c,24,y,352,112,24);
        String name=field.getText().toString().trim();
        avatar(c,62,y+64,26,name.isEmpty()?""+number:name,color);
        text(c,"GIOCATORE "+number,104,y+36,10,color,bold);
        rect(c,104,y+50,256,44,14,CARD_HI);ringRect(c,104,y+50,256,44,14,field.hasFocus()?color:LINE);
        if(field.getVisibility()!=VISIBLE)fit(c,name.isEmpty()?"Giocatore "+number:name,118,y+78,230,16,name.isEmpty()?DIM:TEXT,bold);
    }
    private void vsBadge(Canvas c,float x,float y,float r){glow(c,x,y,r*2.4f,0x55f6b38a);p.setShader(new LinearGradient(x-r,y-r,x+r,y+r,a(ORANGE),a(PINK),Shader.TileMode.CLAMP));p.setColor(0xff000000);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,r,p);p.setShader(null);center(c,"VS",x,y+r*.36f,r*.8f,INK,heavy);}
    private void avatar(Canvas c,float cx,float cy,float r,String name,int color){
        circle(c,cx,cy+r*.2f,r,0x50000000);circle(c,cx,cy+r*.1f,r,darken(color,.55f));orb(c,cx,cy,r,color);
        String initial=name.isEmpty()?"?":new String(Character.toChars(name.codePointAt(0))).toUpperCase(Locale.ITALIAN);
        center(c,initial,cx,cy+r*.36f,r,INK,heavy);
    }

    // ——— Game ———
    private void game(Canvas c){
        if(session==null)return;
        maxScroll=0;scroll=0;
        if(session.phase()==GameSession.Phase.HANDOFF){handoff(c);return;}
        RankedList r=session.ranking;boolean blind=session.isBlind();GameSession.PlayerState player=session.currentPlayer();
        float o=push(c,0);
        roundButton(c,"back",46,36,"Esci dalla partita",this::confirmExit);
        String chip=r.category.toUpperCase(Locale.ITALIAN)+"  ·  "+r.referenceYear;float cw=Math.min(220,measure(chip,9.5f,bold)+28);pill(c,chip,200-cw/2,22,cw,28,CARD_HI,MUTED,9.5f);
        roundButton(c,"info",354,36,"Fonte della classifica",this::source);
        pop(c,o);
        o=push(c,1);float size=keyboard?20:25;float end=wrap(c,r.title,24,keyboard?90:104,352,size,size+4,TEXT,heavy,2);pop(c,o);
        float statsY=end+(keyboard?24:32);int used=player.attemptsUsed();int color=blind?playerColor(session.activePlayerIndex()):LIME;
        o=push(c,2);
        if(blind){avatar(c,36,statsY+2,12,player.name,color);fit(c,"Turno di "+player.name,56,statsY+7,190,14,TEXT,bold);}
        else{star(c,33,statsY+2,8,LIME);text(c,player.score()+" / 10 scoperte",48,statsY+7,14,TEXT,bold);}
        for(int i=0;i<10;i++){float x=260+i*11.8f;float pulse=!reduced()&&i==used-1?1+(float)Math.sin(Math.PI*Motion.progress(now(),lastSubmissionAt,380))*.7f:1;c.save();c.scale(1,pulse,x+4,statsY+1);rect(c,x,statsY-2,8,7,3.5f,i<used?color:CARD_HI);c.restore();}
        if(!reduced()&&now()-lastSubmissionAt<1000)frameAnimating=true;
        textRight(c,(10-used)+(10-used==1?" tentativo":" tentativi"),376,statsY+20,10,MUTED,regular);
        pop(c,o);
        read(24,64,352,statsY-40,r.title+". "+r.subtitle+". "+(blind?"Turno di "+player.name+", alla cieca. "+used+" risposte sigillate.":player.score()+" risposte trovate su 10.")+" "+(10-used)+" tentativi rimasti.");
        boolean playing=session.phase()==GameSession.Phase.PLAYING;
        float top=statsY+(keyboard?26:38),bottom=playing?H-(keyboard?98:116):H-100;
        board(c,top,bottom,blind);
        if(playing){
            float y=H-84,s=shake();
            c.save();c.translate(s,0);
            rect(c,24,y-2,352,66,24,0xff0d1f19);rect(c,24,y+3,352,61,24,CARD_HI);rect(c,24,y+60,352,6,3,0x33000000);ringRect(c,24,y,352,64,24,input.hasFocus()?(color&0xffffff)|0x99000000:LINE);
            c.restore();
            if(!keyboard)center(c,blind?"Nessun indizio: tutto si svela alla fine.":"Accenti e maiuscole non contano.",200,H-96,10,DIM,regular);
        }else if(blind){
            center(c,"Tutte le 20 risposte sono sigillate.",200,H-94,11.5f,MUTED,regular);
            button(c,"Svela i risultati  →",24,H-80,352,58,LILAC,INK,()->show("reveal"));
        }else button(c,"Guarda il risultato  →",24,H-80,352,58,LIME,INK,()->show("results"));
    }
    private void board(Canvas c,float top,float bottom,boolean blind){
        float available=bottom-top;boolean single=available/10f>=40;int rows=single?10:5;float gap=single?6:7;
        float th=Math.max(22,Math.min(single?54:64,(available-gap*(rows-1))/rows)),tw=single?352:172;
        for(int i=0;i<10;i++){
            float x=single?24:24+(i%2)*180,y=top+(single?i:i/2)*(th+gap);
            float o=push(c,enterT(3+i/2));
            if(blind)sealedTile(c,x,y,tw,th,i);else soloTile(c,x,y,tw,th,i,single);
            pop(c,o);
        }
    }
    private void soloTile(Canvas c,float x,float y,float w,float h,int i,boolean wide){
        RankedList r=session.ranking;boolean found=session.currentPlayer().isRevealed(i),done=session.phase()==GameSession.Phase.FINISHED;
        float elapsed=(now()-finishedAt-i*70)/420f;boolean reveal=found||(done&&(reduced()||elapsed>0));
        float turn=1;boolean front=reveal;
        if(!reduced()&&found&&reveals[i]>0){float t=Math.min(1,(now()-reveals[i])/520f);turn=t;if(t<.5f)front=false;if(t<1)frameAnimating=true;}
        if(!reduced()&&done&&!found&&elapsed<1){turn=Math.max(0,elapsed);if(elapsed<.5f)front=false;frameAnimating=true;}
        float radius=Math.min(18,h/2.4f),cy=y+h/2,badge=Math.min(14,h*.3f);
        c.save();flip3d(c,x+w/2,cy,turn,true);
        RankedList.Entry entry=r.entries.get(i);
        if(!front){
            rect(c,x,y+3,w,h,radius,0xff0c1c17);rect(c,x,y,w,h,radius,CARD);ringRect(c,x,y,w,h,radius,LINE);
            circle(c,x+22,cy,badge,CARD_HI);center(c,""+(i+1),x+22,cy+4,11,MUTED,bold);
            float shimmer=.5f+.5f*(float)Math.sin(anim()*2.2f-i*.5f);
            rect(c,x+46,cy-4,wide?150:80,8,4,(Math.round(18+22*shimmer)<<24)|0xffffff);if(wide)rect(c,x+w-70,cy-4,50,8,4,(Math.round(10+14*shimmer)<<24)|0xffffff);
        }else{
            int nameColor=found?INK:MUTED,valueColor=found?0xaa0c2019:DIM;
            if(found){rect(c,x,y+3.5f,w,h,radius,darken(TEAL,.5f));gradRect(c,x,y,w,h,radius,LIME,TEAL);rect(c,x+12,y+2,w-24,h*.32f,h*.16f,0x30ffffff);}else{rect(c,x,y+3,w,h,radius,darken(CARD_HI,.55f));rect(c,x,y,w,h,radius,CARD_HI);}
            circle(c,x+22,cy,badge,found?INK:0x22ffffff);center(c,position(i),x+22,cy+4,11,found?LIME:MUTED,bold);
            if(wide){float vw=Math.min(130,measure(entry.value,10.5f,regular));fit(c,entry.value,x+w-14-vw,cy+4,vw,10.5f,valueColor,regular);fit(c,entry.name,x+46,cy+5,w-vw-74,14,nameColor,bold);}
            else if(h>44){fit(c,entry.name,x+42,cy-1,w-52,13,nameColor,bold);fit(c,entry.value,x+42,cy+15,w-52,9.5f,valueColor,regular);}
            else fit(c,entry.name,x+42,cy+5,w-52,12.5f,nameColor,bold);
        }
        c.restore();
        if(!reduced()&&found&&i==lastHit&&reveals[i]>0){
            float burst=Motion.progress(now(),reveals[i],950);
            if(burst<1){frameAnimating=true;
                if(burst>.45f)ringRect(c,x-3,y-3,w+6,h+6,radius+3,(Math.round(200*(1-burst))<<24)|(LIME&0xffffff));
                for(int j=0;j<12;j++){double angle=j*Math.PI*2/12;float distance=16+Motion.ease(burst)*(wide?60:40);circle(c,x+w/2+(float)Math.cos(angle)*distance*(wide?2.2f:1.2f),cy+(float)Math.sin(angle)*distance*.7f,2.4f*(1-burst),ACCENTS[j%5]);}
            }
        }
        read(x,y,w,h,reveal?"Posizione "+position(i)+". "+entry.name+". "+entry.value+(found?". Trovata.":". Svelata alla fine."):"Posizione "+(i+1)+". Ancora da scoprire.");
    }
    private void sealedTile(Canvas c,float x,float y,float w,float h,int i){
        GameSession.PlayerState player=session.currentPlayer();int used=player.attemptsUsed();int color=playerColor(session.activePlayerIndex());
        float radius=Math.min(18,h/2.4f),cy=y+h/2,badge=Math.min(14,h*.3f);
        if(i<used){
            float t=reduced()||seals[i]==0?1:Motion.progress(now(),seals[i],560);if(t<1)frameAnimating=true;
            float pop=1+(1-Motion.ease(t))*.07f;c.save();c.scale(pop,pop,x+w/2,cy);
            rect(c,x,y+3.5f,w,h,radius,darken(color,.35f));rect(c,x,y,w,h,radius,CARD_HI);ringRect(c,x,y,w,h,radius,(color&0xffffff)|0x66000000);
            if(t<1)rect(c,x,y,w,h,radius,(Math.round(110*(1-t))<<24)|(color&0xffffff));
            orb(c,x+22,cy,badge,color);icon(c,"lock",x+22-7,cy-7,14,INK);
            fit(c,player.guesses().get(i).input,x+44,cy+5,w-56,h>40?14.5f:12.5f,TEXT,bold);
            c.restore();
            read(x,y,w,h,"Risposta "+(i+1)+" sigillata: "+player.guesses().get(i).input);
        }else{
            boolean next=i==used&&session.phase()==GameSession.Phase.PLAYING;
            rect(c,x,y,w,h,radius,(CARD&0xffffff)|0xaa000000);
            float pulse=next&&!reduced()?.5f+.5f*(float)Math.sin(anim()*4):1;
            p.setPathEffect(dashes);ringRect(c,x+.5f,y+.5f,w-1,h-1,radius,next?(Math.round(120+120*pulse)<<24)|(color&0xffffff):LINE);p.setPathEffect(null);
            center(c,""+(i+1),x+22,cy+4,11.5f,next?color:DIM,bold);
            text(c,next?"La prossima risposta…":"Busta vuota",x+44,cy+4,12,next?MUTED:DIM,regular);
            read(x,y,w,h,"Risposta "+(i+1)+": "+(next?"la prossima.":"ancora vuota."));
        }
    }
    private void handoff(Canvas c){
        roundButton(c,"back",46,36,"Metti in pausa la sfida",this::confirmExit);
        float cy=Math.max(176,H*.3f);
        float o=push(c,0);
        glow(c,200,cy,170,0x40c4b2f7);
        float k=reduced()?.5f:(float)(1-Math.cos(anim()*1.7))/2;
        avatar(c,104,cy,34,session.player(0).name,LIME);avatar(c,296,cy,34,session.player(1).name,LILAC);
        float px=136+128*k,py=cy-22-(float)Math.sin(k*Math.PI)*34;
        c.save();c.translate(px,py);c.rotate((k-.5f)*30);rect(c,-14,-22,28,44,7,TEXT);rect(c,-11,-18,22,32,4,INK);circle(c,0,18,1.6f,INK);c.restore();
        centerFit(c,session.player(0).name,104,cy+56,120,12,MUTED,bold);centerFit(c,session.player(1).name,296,cy+56,120,12,MUTED,bold);
        if(!reduced())frameAnimating=true;
        pop(c,o);
        o=push(c,1);
        center(c,"Tocca a",200,cy+112,16,MUTED,regular);centerFit(c,session.player(1).name,200,cy+152,350,36,LILAC,heavy);
        centerFit(c,session.player(0).name+" ha sigillato le sue 10 risposte.",200,cy+188,350,13,MUTED,regular);
        center(c,"Passa il telefono senza sbirciare!",200,cy+210,13,MUTED,regular);
        pop(c,o);
        read(24,cy+80,352,140,"Tocca a "+session.player(1).name+". "+session.player(0).name+" ha sigillato le sue 10 risposte. Passa il telefono senza sbirciare.");
        o=push(c,2);button(c,"Sono "+session.player(1).name+", si parte!",24,Math.min(H-88,cy+248),352,58,LILAC,INK,this::startSecondPlayer);pop(c,o);
    }

    // ——— Reveal: both sealed boards open card by card ———
    private void reveal(Canvas c){
        if(session==null||session.mode!=GameSession.Mode.DUEL||session.phase()!=GameSession.Phase.FINISHED){return;}
        pageStart(c);
        long step=430,start=revealAt+500,doneAt=start+19*step+380;
        int opened=reduced()?20:(int)Math.max(0,Math.min(20,now()<start?0:(now()-start)/step+1));
        if(!capturing&&opened>revealCount){
            boolean hit=false;for(int j=revealCount;j<opened;j++){GameSession.Guess g=guessAt(j%2,j/2);if(g!=null&&g.status==GameSession.GuessStatus.HIT)hit=true;}
            revealCount=opened;if(!reduced()){if(hit)feedback(true);else tone(ToneGenerator.TONE_PROP_BEEP,40);}
        }
        boolean done=reduced()||now()>=doneAt;if(!done)frameAnimating=true;
        int[] scores=new int[2];for(int j=0;j<opened;j++){GameSession.Guess g=guessAt(j%2,j/2);if(g!=null&&g.status==GameSession.GuessStatus.HIT)scores[j%2]++;}
        int winner=done?session.winnerIndex():-1;
        if(done){
            float k=reduced()?1:Motion.ease(Motion.progress(now(),doneAt,500));
            String headline=winner<0?"Pareggio perfetto!":session.player(winner).name+" vince!";
            c.save();float zoom=.7f+.3f*k;c.scale(zoom,zoom,200,38);float before=alphaMul;alphaMul*=k;centerFit(c,headline,200,46,350,26,winner<0?ORANGE:playerColor(winner),heavy);alphaMul=before;c.restore();
            confetti(c,0,H,36);
        }else{
            center(c,"LA RESA DEI CONTI",200,40,11,LILAC,bold);
            pill(c,"Salta",312,20,64,32,CARD_HI,TEXT,11);hit(306,14,76,44,"Salta l’animazione",()->revealAt=now()-100000);
        }
        for(int pl=0;pl<2;pl++){
            float x=pl==0?24:210;boolean lead=done&&winner==pl;
            card(c,x,64,166,112,24);if(lead){ringRect(c,x,64,166,112,24,playerColor(pl));c.save();clipRound(c,x,64,166,112,24);glow(c,x+83,90,110,(playerColor(pl)&0xffffff)|0x40000000);c.restore();}
            avatar(c,x+36,100,20,session.player(pl).name,playerColor(pl));
            if(lead)icon(c,"crown",x+26,58,20,ORANGE);
            fit(c,session.player(pl).name,x+64,106,90,13,TEXT,bold);
            text(c,""+scores[pl],x+18,164,32,TEXT,heavy);text(c,"/ 10",x+20+measure(""+scores[pl],32,heavy)+4,163,13,MUTED,bold);
        }
        vsBadge(c,200,120,18);
        read(24,64,352,112,session.player(0).name+" "+scores[0]+", "+session.player(1).name+" "+scores[1]+(done?(winner<0?". Pareggio.":". Vince "+session.player(winner).name+"."):""));
        float top=194,rowH=Math.max(40,Math.min(52,(clipBottom-top-10)/10f-6));
        for(int row=0;row<10;row++){
            float y=top+row*(rowH+6);
            circle(c,200,y+rowH/2,11,CARD_HI);center(c,""+(row+1),200,y+rowH/2+4,10,MUTED,bold);
            for(int pl=0;pl<2;pl++)revealCard(c,pl==0?24:214,y,162,rowH,guessAt(pl,row),start+(row*2+pl)*step,pl);
        }
        pageEnd(c,top+10*(rowH+6)+10);
        stickyBar(c);
        if(done)button(c,"Classifica completa  →",24,H-80,352,58,LIME,INK,()->show("results"));
        else center(c,"Le buste si aprono una alla volta…",200,H-46,12.5f,MUTED,regular);
    }
    private GameSession.Guess guessAt(int player,int index){List<GameSession.Guess> list=session.player(player).guesses();return index<list.size()?list.get(index):null;}
    private void revealCard(Canvas c,float x,float y,float w,float h,GameSession.Guess guess,long time,int player){
        float t=reduced()?1:Motion.progress(now(),time,340);boolean open=reduced()||now()>=time+170;
        float radius=Math.min(16,h/2.6f),cy=y+h/2;
        c.save();flip3d(c,x+w/2,cy,t,true);
        if(guess!=null)rect(c,x,y+3.5f,w,h,radius,open&&guess.status==GameSession.GuessStatus.HIT?darken(TEAL,.5f):0xff0c1c17);
        if(guess==null){rect(c,x,y,w,h,radius,CARD);}
        else if(!open){rect(c,x,y,w,h,radius,CARD_HI);circle(c,x+20,cy,12,(playerColor(player)&0xffffff)|0x55000000);icon(c,"lock",x+13,cy-7,14,playerColor(player));center(c,"?",x+w/2+12,cy+5,14,DIM,heavy);}
        else if(guess.status==GameSession.GuessStatus.HIT){gradRect(c,x,y,w,h,radius,LIME,TEAL);rect(c,x+10,y+2,w-20,h*.3f,h*.15f,0x30ffffff);circle(c,x+20,cy,12,INK);center(c,position(guess.entryIndex),x+20,cy+4,10,LIME,bold);fit(c,session.ranking.entries.get(guess.entryIndex).name,x+38,cy+5,w-46,12.5f,INK,bold);}
        else{rect(c,x,y,w,h,radius,0xff33242a);circle(c,x+20,cy,12,0x33ff8b84);icon(c,"close",x+14,cy-6,12,RED);fit(c,guess.input,x+38,guess.entryIndex>=0&&h>44?cy:cy+5,w-46,12,MUTED,bold);if(guess.entryIndex>=0&&h>44)text(c,"già trovata",x+38,cy+14,9,DIM,regular);}
        c.restore();
        if(guess!=null)read(x,y,w,h,session.player(player).name+", risposta: "+(open?guess.input+(guess.status==GameSession.GuessStatus.HIT?", posizione "+position(guess.entryIndex):", non in classifica"):"ancora sigillata"));
    }
    private void stickyBar(Canvas c){p.setShader(new LinearGradient(0,H-130,0,H-90,BG&0xffffff,BG,Shader.TileMode.CLAMP));p.setColor(0xff000000);p.setStyle(Paint.Style.FILL);c.drawRect(0,H-130,400,H,p);p.setShader(null);}
    private void confetti(Canvas c,float top,float height,int count){
        float t=anim();
        for(int i=0;i<count;i++){
            float speed=38+(i*37)%64,x=(i*83+13)%392+4+(float)Math.sin(t*1.5f+i)*12,y=top+((t*speed+i*53)%height);
            c.save();c.rotate(t*140+i*33,x,y);rect(c,x-2.5f,y-5,5,10,1.5f,(0xcc<<24)|(ACCENTS[i%5]&0xffffff));c.restore();
        }
        if(!reduced())frameAnimating=true;
    }

    // ——— Results ———
    private void results(Canvas c){
        if(session==null)return;
        pageStart(c);
        boolean duel=session.mode==GameSession.Mode.DUEL;int score=session.player(0).score();
        float o=push(c,0);brand(c,24,44,.9f);roundButton(c,"close",354,36,"Torna alla home",()->show("home"));pop(c,o);
        if(scroll<260&&(duel||score>0))confetti(c,60,220,26);
        o=push(c,1);
        float k=reduced()?1:Motion.ease(Motion.progress(now(),pageAt+200,1200));if(k<1)frameAnimating=true;
        if(!duel){
            glow(c,200,152,120,0x44dcf58d);
            ring(c,200,152,62,13,0,LIME,CARD_HI);
            p.setShader(new SweepGradient(200,152,new int[]{a(TEAL),a(LIME),a(TEAL)},null));ring(c,200,152,62,13,score/10f*k,LIME,0);p.setShader(null);
            center(c,""+Math.round(score*k),200,168,46,TEXT,heavy);center(c,"SU 10",200,190,9.5f,MUTED,bold);
        }else{
            int winner=session.winnerIndex();
            for(int pl=0;pl<2;pl++){float cx=pl==0?118:282;if(winner==pl){glow(c,cx,140,80,(playerColor(pl)&0xffffff)|0x55000000);icon(c,"crown",cx-14,74,28,ORANGE);}avatar(c,cx,140,34,session.player(pl).name,playerColor(pl));center(c,""+Math.round(session.player(pl).score()*k),cx,214,30,TEXT,heavy);centerFit(c,session.player(pl).name,cx,236,140,12,MUTED,bold);}
            vsBadge(c,200,150,18);
        }
        pop(c,o);
        o=push(c,2);
        String headline=duel?(session.winnerIndex()<0?"Una sfida alla pari!":session.player(session.winnerIndex()).name+" vince!"):(score==10?"Sei sulla vetta!":score>=7?"Che bella scalata!":score>=4?"L’intuito c’è!":"La curiosità vince sempre.");
        float y=duel?280:262;
        centerFit(c,headline,200,y,350,27,TEXT,heavy);
        centerFit(c,duel?"Stessa classifica, risposte alla cieca.":"risposte trovate con "+session.player(0).attemptsUsed()+" tentativi",200,y+26,350,13,MUTED,regular);
        read(24,60,352,y-30,headline+". "+session.player(0).name+": "+score+" risposte trovate su 10."+(duel?" "+session.player(1).name+": "+session.player(1).score()+" risposte trovate su 10.":""));
        pop(c,o);
        y+=64;
        o=push(c,3);text(c,"La classifica completa",24,y,20,TEXT,heavy);pop(c,o);y+=18;
        int[] medals={0xffffd36e,0xffdfe6ee,0xffe9a97e};
        for(int i=0;i<10;i++){
            o=push(c,4+Math.min(i,7));
            RankedList.Entry entry=session.ranking.entries.get(i);boolean one=session.player(0).isRevealed(i),two=duel&&session.player(1).isRevealed(i);boolean found=one||two;
            card(c,24,y,352,58,18);if(found){c.save();clipRound(c,24,y,352,58,18);glow(c,24,y+29,120,0x33dcf58d);c.restore();}
            circle(c,50,y+29,15,i<3?medals[i]:CARD_HI);center(c,position(i),50,y+33,11.5f,i<3?INK:MUTED,bold);
            fit(c,entry.name,76,y+26,duel?224:250,14,found?TEXT:MUTED,bold);fit(c,entry.value,76,y+44,250,10.5f,DIM,regular);
            if(duel){if(one)avatar(c,two?324:350,y+29,10,session.player(0).name,LIME);if(two)avatar(c,350,y+29,10,session.player(1).name,LILAC);}
            else if(found){circle(c,350,y+29,12,LIME);icon(c,"check",343,y+22,14,INK);}
            read(24,y,352,58,"Posizione "+position(i)+". "+entry.name+". "+entry.value+(duel?(one?". Trovata da "+session.player(0).name:"")+(two?". Trovata da "+session.player(1).name:""):found?". Trovata.":""));
            pop(c,o);y+=66;
        }
        y+=8;
        o=push(c,12);
        pressTransform(c,24,y,352,70);card(c,24,y,352,70,20);circle(c,52,y+35,16,CARD_HI);icon(c,"info",43,y+26,18,TEAL);
        fit(c,session.ranking.sourceName,80,y+31,250,12.5f,TEXT,bold);text(c,"Fonte · "+session.ranking.referenceYear+" · apri il metodo",80,y+50,10.5f,MUTED,regular);icon(c,"next",344,y+27,16,MUTED);c.restore();
        hit(24,y,352,70,"Consulta la fonte dei dati",this::source);
        pop(c,o);y+=86;
        o=push(c,13);button(c,"Torna alla home",24,y,352,50,CARD_HI,TEXT,()->show("home"));pop(c,o);
        pageEnd(c,y+70);
        stickyBar(c);
        button(c,duel?"Nuova sfida  →":"Un’altra Top 10  →",24,H-80,352,58,LIME,INK,this::playAgain);
    }

    // ——— Dock ———
    private void nav(Canvas c){
        float d=dockProgress();if(d<=.001f)return;
        if(!reduced()&&(now()-navAt<420||now()-dockAt<380))frameAnimating=true;
        float shiftBefore=pageShift,before=alphaMul;pageShift=0;alphaMul=d;
        c.save();c.translate(0,(1-d)*130);
        p.setShader(dockFade);p.setColor(a(0xff000000));p.setStyle(Paint.Style.FILL);c.drawRect(0,H-170,400,H,p);p.setShader(null);
        float y=H-90;
        rect(c,24,y+8,352,70,35,0x66000000);rect(c,24,y,352,70,35,0xf2132a22);ringRect(c,24,y,352,70,35,LINE);
        float slot=112,position=navPosition();
        rect(c,32+slot*position,y+11,slot,54,27,darken(TEAL,.5f));gradRect(c,32+slot*position,y+8,slot,54,27,LIME,TEAL);rect(c,44+slot*position,y+10,slot-24,18,9,0x33ffffff);
        String[] labels={"Gioca","Classifiche","Profilo"},pages={"home","library","profile"},symbols={"home","grid","user"};
        for(int i=0;i<3;i++){
            float x=32+slot*i+slot/2,lit=Math.max(0,1-Math.abs(position-i));int color=blend(MUTED,INK,lit);
            icon(c,symbols[i],x-10,y+14,20,color);center(c,labels[i],x,y+52,10,color,bold);
            final String page=pages[i];if(d>.98f)hit(32+slot*i,y+4,slot,62,labels[i]+(screen.equals(page)?", selezionata":""),()->{if(!screen.equals(page))show(page);});
        }
        c.restore();alphaMul=before;pageShift=shiftBefore;
    }
    private int blend(int a,int b,float t){return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}
    private void toast(Canvas c){
        if(notice.isEmpty()||now()-noticeAt>=2600||!modal.isEmpty())return;
        float y=screen.equals("game")?H-(keyboard?140:158):dockPage(screen)?H-150:H-112;
        float age=now()-noticeAt,t=reduced()?1:Math.min(Motion.ease(age/220f),Motion.ease((2600-age)/240f));
        float w=Math.min(352,measure(notice,12.5f,bold)+48);
        c.save();c.translate(0,(1-t)*14);float before=alphaMul;alphaMul=t;
        rect(c,200-w/2,y+4,w,42,21,0x55000000);rect(c,200-w/2,y,w,42,21,0xfa24443a);ringRect(c,200-w/2,y,w,42,21,LINE);
        centerFit(c,notice,200,y+26,w-28,12.5f,TEXT,bold);
        alphaMul=before;c.restore();
        if(reduced())postInvalidateDelayed((long)(2600-age)+1);else frameAnimating=true;
    }

    // ——— Sheets ———
    private void modal(Canvas c){
        float progress=sheetProgress();if(!reduced()&&Motion.progress(now(),sheetAt,sheetClosing?240:380)<1)frameAnimating=true;
        c.drawColor((Math.round(170*progress*(1-Math.min(.7f,sheetDrag/sheetHeight())))<<24)|0x020806);
        if(!capturing)hits.clear();pageShift=0;float y=sheetTop();
        rect(c,0,y,400,H-y+40,32,SHEET);ringRect(c,0,y,400,H-y+40,32,LINE);rect(c,176,y+10,48,5,2.5f,0x40ffffff);
        circle(c,352,y+42,19,CARD_HI);icon(c,"close",344,y+34,16,TEXT);hit(326,y+16,52,52,"Chiudi",this::closeSheet);
        if(modal.equals("settings")){
            text(c,"Impostazioni",24,y+68,27,TEXT,heavy);text(c,"Piccoli dettagli, una bella partita.",24,y+94,13,MUTED,regular);
            card(c,24,y+114,352,198,22);
            setting(c,y+114,"Suoni","Una nota per ogni scoperta","sound",true);line(c,44,y+180,356,y+180,LINE,1);
            setting(c,y+180,"Vibrazione","Senti quando ci hai preso","haptic",true);line(c,44,y+246,356,y+246,LINE,1);
            setting(c,y+246,"Animazioni ridotte","Un’esperienza più tranquilla","reduced",false);
            updateCard(c,y+326);
            pressTransform(c,24,y+444,352,56);card(c,24,y+444,352,56,18);icon(c,"play",42,y+462,20,LIME);text(c,"Riguarda l’introduzione",74,y+477,13.5f,TEXT,bold);icon(c,"next",344,y+464,16,MUTED);c.restore();
            hit(24,y+444,352,56,"Riguarda introduzione animata",this::openOnboarding);
            center(c,"TOPPERIA "+updater.currentVersionName()+" · FATTO PER MENTI CURIOSE",200,y+540,8.5f,DIM,bold);
        }else if(modal.equals("rules")){
            text(c,"Come si gioca",24,y+68,27,TEXT,heavy);text(c,"Dieci posti. Dieci tentativi. Tanta curiosità.",24,y+94,13,MUTED,regular);
            rule(c,y+120,1,LIME,"Pensa alla Top 10","Scrivi un elemento che secondo te è in classifica: la posizione non serve.");
            rule(c,y+196,2,TEAL,"Scopri il suo posto","Se c’è, la casella si gira e mostra posizione e dato.");
            rule(c,y+272,3,ORANGE,"Dieci tentativi","Ogni risposta nuova ne usa uno. Ripetizioni e risposte vuote sono gratis.");
            rule(c,y+348,4,LILAC,"In due, alla cieca","Nella sfida nessuno vede i propri risultati fino alla resa dei conti finale.");
            button(c,"Ci sono, giochiamo!",24,y+446,352,56,LIME,INK,this::closeSheet);
        }else if(modal.equals("detail")&&detailRanking!=null){
            RankedList r=detailRanking;int accent=accent(r.category);
            glow(c,60,y+70,110,(accent&0xffffff)|0x40000000);
            iconBadge(c,r.icon,24,y+36,62,accent);
            text(c,r.category.toUpperCase(Locale.ITALIAN)+"  ·  "+r.referenceYear,102,y+62,10,accent,bold);
            if(store.option("played_"+r.id,false))text(c,"✓ GIÀ GIOCATA",102,y+82,9.5f,LIME,bold);
            float end=wrap(c,r.title,24,y+138,352,24,29,TEXT,heavy,3);
            end=wrap(c,r.subtitle,24,end+28,352,13.5f,19,MUTED,regular,2);
            icon(c,"info",24,end+14,15,DIM);fit(c,"Fonte: "+r.sourceName,46,end+26,326,11,DIM,regular);hit(24,end+6,352,30,"Fonte della classifica",this::source);
            read(24,y+30,352,end-y-20,r.title+". "+r.subtitle+". "+r.category+", "+r.referenceYear+".");
            float by=y+sheetHeight()-148;
            button(c,"Gioca da solo",24,by,352,56,LIME,INK,()->playDetail(GameSession.Mode.SOLO));
            button(c,"Sfida 1 contro 1",24,by+66,352,56,LILAC,INK,()->playDetail(GameSession.Mode.DUEL));
        }
    }
    private void rule(Canvas c,float y,int n,int color,String title,String body){circle(c,44,y+20,19,color);center(c,""+n,44,y+26,16,INK,heavy);text(c,title,78,y+17,16,TEXT,bold);wrap(c,body,78,y+39,296,12.5f,17,MUTED,regular,2);}
    private float switchPosition(String key,boolean fallback){float target=store.option(key,fallback)?1:0;return !reduced()&&toggleKey.equals(key)?Motion.lerp(toggleFrom,target,Motion.ease(Motion.progress(now(),toggleAt,240))):target;}
    private void setting(Canvas c,float y,String title,String desc,String key,boolean fallback){
        text(c,title,44,y+29,15,TEXT,bold);text(c,desc,44,y+49,11,MUTED,regular);boolean on=store.option(key,fallback);float t=switchPosition(key,fallback);
        if(!reduced()&&toggleKey.equals(key)&&now()-toggleAt<240)frameAnimating=true;
        rect(c,302,y+18,54,30,15,blend(0xff2e4a41,LIME,t));circle(c,317+24*t,y+35,11,0x55000000);orb(c,317+24*t,y+33,11,blend(MUTED,WHITE,t));
        hit(24,y,352,66,title+(on?", attivo":", disattivo"),()->{toggleFrom=switchPosition(key,fallback);toggleKey=key;toggleAt=now();store.toggle(key,fallback);readMotionPreference();invalidate();});
    }
    private void updateCard(Canvas c,float y){
        card(c,24,y,352,104,22);text(c,"AGGIORNAMENTI",44,y+26,9,MUTED,bold);
        String label=updater.isBusy()?"Attendi…":updateReady?"Installa":availableUpdate!=null?"Scarica":"Verifica";
        pill(c,label,262,y+14,96,34,updater.isBusy()?CARD_HI:LIME,updater.isBusy()?MUTED:INK,11);hit(256,y+8,108,46,label+" aggiornamenti",this::updateAction);
        fit(c,availableUpdate==null?"Topperia "+updater.currentVersionName():"Disponibile "+availableUpdate.versionName,44,y+50,210,16,TEXT,bold);
        fit(c,updateStatus,44,y+72,312,11,MUTED,regular);
        if(downloadProgress>=0){rect(c,44,y+86,312,5,2.5f,CARD_HI);rect(c,44,y+86,312*Math.max(0,Math.min(1,downloadProgress/100f)),5,2.5f,LIME);}else text(c,"Distribuita dalla repository ufficiale su GitHub",44,y+92,9,DIM,regular);
        read(30,y,224,102,"Aggiornamenti. Versione installata "+updater.currentVersionName()+". "+updateStatus);
    }

    // ——— Drawing primitives ———
    private int a(int color){if(alphaMul>=.999f)return color;return (Math.round((color>>>24)*Math.max(0,alphaMul))<<24)|(color&0xffffff);}
    private float pressAmount(float x,float y,float w,float h){if(pressedBounds==null||reduced()||Math.abs(pressedBounds.width()-w)>=3||Math.abs(pressedBounds.left-(x+pageShift))>=3||Math.abs(pressedBounds.top-(y+hitOffset))>=3)return 0;frameAnimating=true;return pressRelease==0?1-Motion.ease(Motion.progress(now(),pressAt,260)):Motion.ease(Motion.progress(now(),pressAt,110));}
    // Cards tip toward the finger, like a physical tile pressed off-centre.
    private void pressTransform(Canvas c,float x,float y,float w,float h){
        c.save();float t=pressAmount(x,y,w,h);if(t<=0)return;
        float cx=x+w/2,cy=y+h/2,nx=Math.max(-1,Math.min(1,(downX-pageShift-cx)/(w/2))),ny=Math.max(-1,Math.min(1,(downY-hitOffset-cy)/(h/2)));
        rotate3d(c,cx,cy,-ny*9*t,nx*9*t);float size=1-.025f*t;c.scale(size,size,cx,cy);
    }
    private void rotate3d(Canvas c,float cx,float cy,float degreesX,float degreesY){camera.save();camera.rotateX(degreesX);camera.rotateY(degreesY);camera.getMatrix(tilt);camera.restore();tilt.preTranslate(-cx,-cy);tilt.postTranslate(cx,cy);c.concat(tilt);}
    /** Half-turn flip: the back shows until the midpoint, then the front swings in. */
    private void flip3d(Canvas c,float cx,float cy,float progress,boolean aroundX){if(progress<=0||progress>=1)return;float degrees=progress<.5f?progress*180:(progress-1)*180;if(aroundX)rotate3d(c,cx,cy,degrees,0);else rotate3d(c,cx,cy,0,degrees);}
    private int darken(int color,float amount){return (color&0xff000000)|((int)(Color.red(color)*amount)<<16)|((int)(Color.green(color)*amount)<<8)|(int)(Color.blue(color)*amount);}
    private int lighten(int color,float amount){return (color&0xff000000)|((int)(Color.red(color)+(255-Color.red(color))*amount)<<16)|((int)(Color.green(color)+(255-Color.green(color))*amount)<<8)|(int)(Color.blue(color)+(255-Color.blue(color))*amount);}
    /** A lit sphere: base colour with a soft highlight up and to the left. */
    private void orb(Canvas c,float cx,float cy,float r,int color){circle(c,cx,cy,r,color);int light=a(0x70ffffff);p.setShader(new RadialGradient(cx-r*.38f,cy-r*.42f,r*1.15f,new int[]{light,light&0xffffff,a(0x30000000)},new float[]{0,.55f,1},Shader.TileMode.CLAMP));p.setColor(0xff000000);p.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy,r,p);p.setShader(null);}
    // Push buttons have a visible side: the face sinks onto its base while pressed.
    private void button(Canvas c,String title,float x,float y,float w,float h,int bg,int fg,Runnable action){
        float depth=6,t=pressAmount(x,y,w,h+depth),sink=depth*t,r=h/2.4f;
        rect(c,x+3,y+depth+5-sink*.5f,w-6,h,r,0x40000000);rect(c,x,y+depth,w,h,r,darken(bg,bg==CARD_HI?.5f:.55f));
        gradVertical(c,x,y+sink,w,h,r,lighten(bg,.14f),bg);
        if(bg!=CARD_HI)rect(c,x+12,y+sink+3,w-24,h*.36f,h*.18f,0x2effffff);else ringRect(c,x,y+sink,w,h,r,LINE);
        line(c,x+r,y+sink+1.2f,x+w-r,y+sink+1.2f,0x55ffffff,1.2f);
        centerFit(c,title,x+w/2,y+sink+h/2+5.5f,w-28,15.5f,fg,bold);
        hit(x,y,w,h+depth,title,action);
    }
    private void gradVertical(Canvas c,float x,float y,float w,float h,float r,int top,int bottom){p.setShader(new LinearGradient(x,y,x,y+h,a(top),a(bottom),Shader.TileMode.CLAMP));p.setColor(0xff000000);p.setStyle(Paint.Style.FILL);c.drawRoundRect(x,y,x+w,y+h,r,r,p);p.setShader(null);}
    private void card(Canvas c,float x,float y,float w,float h,float r){rect(c,x+2,y+9,w-4,h,r,0x40000000);rect(c,x,y+4,w,h,r,0xff0c1c17);gradVertical(c,x,y,w,h,r,lighten(CARD,.04f),CARD);ringRect(c,x,y,w,h,r,LINE);line(c,x+r,y+1,x+w-r,y+1,0x22ffffff,1);}
    private void clipRound(Canvas c,float x,float y,float w,float h,float r){clipPath.rewind();clipPath.addRoundRect(x,y,x+w,y+h,r,r,Path.Direction.CW);c.clipPath(clipPath);}
    private void gradRect(Canvas c,float x,float y,float w,float h,float r,int from,int to){p.setShader(new LinearGradient(x,y,x+w,y+h,a(from),a(to),Shader.TileMode.CLAMP));p.setColor(0xff000000);p.setStyle(Paint.Style.FILL);c.drawRoundRect(x,y,x+w,y+h,r,r,p);p.setShader(null);}
    private void glow(Canvas c,float x,float y,float r,int color){if(c.quickReject(x-r,y-r,x+r,y+r,Canvas.EdgeType.AA))return;int start=a(color);p.setShader(new RadialGradient(x,y,r,start,start&0xffffff,Shader.TileMode.CLAMP));p.setColor(0xff000000);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,r,p);p.setShader(null);}
    private void ring(Canvas c,float cx,float cy,float r,float width,float progress,int color,int track){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);
        if(track!=0){Shader shader=p.getShader();p.setShader(null);p.setColor(a(track));c.drawCircle(cx,cy,r,p);p.setShader(shader);}
        if(progress>0){if(p.getShader()==null)p.setColor(a(color));else p.setColor(a(0xff000000));c.drawArc(cx-r,cy-r,cx+r,cy+r,-90,360*Math.min(1,progress),false,p);}
        p.setStyle(Paint.Style.FILL);
    }
    private void logoMark(Canvas c,float x,float y,float size){if(logo!=null){logoPaint.setAlpha(Math.round(255*Math.max(0,Math.min(1,alphaMul))));c.drawBitmap(logo,null,new RectF(x,y,x+size,y+size),logoPaint);}}
    private void brand(Canvas c,float x,float y,float size){c.save();c.translate(x,y);c.scale(size,size);glow(c,17,-13,34,0x30dcf58d);logoMark(c,-7,-38,49);text(c,"topperia",43,-1,25,TEXT,heavy);c.restore();}
    private void trophy(Canvas c,float x,float y,int color){if(c.quickReject(x-65,y-80,x+65,y+45,Canvas.EdgeType.AA))return;if(!reduced())frameAnimating=true;c.save();c.translate(x,y);float b=(float)Math.sin(anim()*1.8)*2;c.translate(0,b);stroke(c,-37,-24,74,34,16,color,5);p.setColor(a(color));Path cup=cupPath;cup.rewind();cup.moveTo(-28,-34);cup.lineTo(28,-34);cup.lineTo(22,0);cup.quadTo(0,26,-22,0);cup.close();c.drawPath(cup,p);rect(c,-4,10,8,18,3,color);rect(c,-19,26,38,8,4,color);circle(c,-8,-13,2.5f,INK);circle(c,8,-13,2.5f,INK);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.4f);p.setColor(a(INK));c.drawArc(-7,-12,7,1,15,150,false,p);p.setStyle(Paint.Style.FILL);star(c,43,-41,7,WHITE);c.restore();}
    private void iconBadge(Canvas c,String name,float x,float y,float s,int bg){float r=s*.32f;rect(c,x,y+s*.09f,s,s,r,darken(bg,.55f));gradVertical(c,x,y,s,s,r,lighten(bg,.25f),bg);rect(c,x+s*.14f,y+s*.05f,s*.72f,s*.3f,s*.15f,0x33ffffff);icon(c,name,x+s*.24f,y+s*.24f,s*.52f,INK);}
    private void icon(Canvas c,String name,float x,float y,float size,int color){c.save();c.translate(x,y);c.scale(size/24,size/24);p.setShader(null);p.setColor(a(color));p.setStrokeWidth(1.9f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);Path a=iconPath;a.rewind();
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
            case "next":a.moveTo(9,4);a.lineTo(17,12);a.lineTo(9,20);c.drawPath(a,p);break;
            case "close":c.drawLine(6,6,18,18,p);c.drawLine(18,6,6,18,p);break;
            case "check":p.setStrokeWidth(2.6f);a.moveTo(5,12.5f);a.lineTo(10,17.5f);a.lineTo(19,7);c.drawPath(a,p);break;
            case "lock":c.drawRoundRect(5,10,19,21,3,3,p);c.drawArc(8,3,16,15,180,180,false,p);c.drawLine(8,9,8,10,p);c.drawLine(16,9,16,10,p);c.drawLine(12,14,12,17,p);break;
            case "crown":p.setStyle(Paint.Style.FILL);a.moveTo(3,18);a.lineTo(4,7);a.lineTo(9,12);a.lineTo(12,4);a.lineTo(15,12);a.lineTo(20,7);a.lineTo(21,18);a.close();c.drawPath(a,p);c.drawRect(3,19,21,21,p);break;
            case "target":c.drawCircle(12,12,10,p);c.drawCircle(12,12,5.5f,p);c.drawCircle(12,12,1.2f,p);break;
            case "play":a.moveTo(8,4);a.lineTo(19,12);a.lineTo(8,20);a.close();c.drawPath(a,p);break;
            case "info":c.drawCircle(12,12,9,p);c.drawLine(12,11,12,17,p);c.drawCircle(12,7,.7f,p);break;
            case "settings":c.drawCircle(12,12,4,p);c.drawCircle(12,12,9,p);for(int i=0;i<8;i++){double t=i*Math.PI/4;c.drawLine(12+(float)Math.cos(t)*9,12+(float)Math.sin(t)*9,12+(float)Math.cos(t)*11,12+(float)Math.sin(t)*11,p);}break;
            case "home":a.moveTo(3,10);a.lineTo(12,3);a.lineTo(21,10);a.lineTo(20,21);a.lineTo(15,21);a.lineTo(15,14);a.lineTo(9,14);a.lineTo(9,21);a.lineTo(4,21);a.close();c.drawPath(a,p);break;
            case "grid":for(int i=0;i<4;i++)c.drawRoundRect(3+(i%2)*11,3+(i/2)*11,10+(i%2)*11,10+(i/2)*11,2,2,p);break;
            case "solo":case "user":c.drawCircle(12,7,4,p);c.drawArc(3,13,21,28,180,180,false,p);break;
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
    private void star(Canvas c,float x,float y,float r,int color){Path a=starPath;a.rewind();for(int i=0;i<8;i++){double t=-Math.PI/2+i*Math.PI/4;float rr=i%2==0?r:r*.38f;float xx=x+(float)Math.cos(t)*rr,yy=y+(float)Math.sin(t)*rr;if(i==0)a.moveTo(xx,yy);else a.lineTo(xx,yy);}a.close();p.setShader(null);p.setColor(a(color));p.setStyle(Paint.Style.FILL);c.drawPath(a,p);}
    private void rect(Canvas c,float x,float y,float w,float h,float radius,int color){p.setShader(null);p.setColor(a(color));p.setStyle(Paint.Style.FILL);c.drawRoundRect(x,y,x+w,y+h,radius,radius,p);}
    private void stroke(Canvas c,float x,float y,float w,float h,float radius,int color,float width){p.setShader(null);p.setColor(a(color));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);c.drawRoundRect(x,y,x+w,y+h,radius,radius,p);p.setStyle(Paint.Style.FILL);}
    private void ringRect(Canvas c,float x,float y,float w,float h,float radius,int color){stroke(c,x+.5f,y+.5f,w-1,h-1,radius,color,1.2f);}
    private void ringStroke(Canvas c,float x,float y,float r,int color,float width){p.setShader(null);p.setColor(a(color));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);c.drawCircle(x,y,r,p);p.setStyle(Paint.Style.FILL);}
    private void circle(Canvas c,float x,float y,float r,int color){p.setShader(null);p.setColor(a(color));p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,r,p);}
    private void line(Canvas c,float x,float y,float xx,float yy,int color,float width){p.setShader(null);p.setColor(a(color));p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x,y,xx,yy,p);}
    private void text(Canvas c,String s,float x,float y,float size,int color,Typeface face){p.setShader(null);p.setColor(a(color));p.setTypeface(face);p.setTextSize(size);p.setStyle(Paint.Style.FILL);c.drawText(s,x,y,p);}
    private void textRight(Canvas c,String s,float x,float y,float size,int color,Typeface face){text(c,s,x-measure(s,size,face),y,size,color,face);}
    private float measure(String s,float size,Typeface face){p.setTypeface(face);p.setTextSize(size);return p.measureText(s);}
    private static final class FittedText{final String text;final float size;FittedText(String text,float size){this.text=text;this.size=size;}}
    private void fit(Canvas c,String s,float x,float y,float width,float size,int color,Typeface face){
        if(c.quickReject(x,y-size,x+width,y+size*.3f,Canvas.EdgeType.AA))return;
        String key=s+'\n'+width+':'+size+':'+(face==heavy?2:face==bold?1:0);FittedText fitted=fittedText.get(key);
        if(fitted==null){while(size>8&&measure(s,size,face)>width)size=Math.max(8,size-.3f);if(measure(s,size,face)>width){while(s.length()>1&&measure(s+"…",size,face)>width)s=s.substring(0,s.length()-1);s+="…";}fitted=new FittedText(s,size);if(fittedText.size()>320)fittedText.clear();fittedText.put(key,fitted);}
        text(c,fitted.text,x,y,fitted.size,color,face);
    }
    private void center(Canvas c,String s,float x,float y,float size,int color,Typeface face){text(c,s,x-measure(s,size,face)/2,y,size,color,face);}
    private void centerFit(Canvas c,String s,float x,float y,float width,float size,int color,Typeface face){while(size>11&&measure(s,size,face)>width)size-=.5f;center(c,s,x,y,size,color,face);}
    private void pill(Canvas c,String title,float x,float y,float w,float h,int bg,int fg,float size){if(bg!=0)rect(c,x,y,w,h,h/2,bg);center(c,title,x+w/2,y+h/2+size*.35f,size,fg,bold);}
    private float wrap(Canvas c,String value,float x,float y,float width,float size,float spacing,int color,Typeface face,int max){
        String key=value+'\n'+width+':'+size+':'+max+':'+(face==heavy?2:face==bold?1:0);
        String[] lines=wrappedText.get(key);
        if(lines==null){
            ArrayList<String> rows=new ArrayList<>();String row="";
            for(String word:value.split(" ")){if(!row.isEmpty()&&measure(row+" "+word,size,face)>width){rows.add(row);row="";if(rows.size()==max)break;}row+=row.isEmpty()?word:" "+word;}
            if(!row.isEmpty()&&rows.size()<max)rows.add(row);
            lines=rows.toArray(new String[0]);if(wrappedText.size()>240)wrappedText.clear();wrappedText.put(key,lines);
        }
        if(!c.quickReject(x,y-size,x+width,y+lines.length*spacing,Canvas.EdgeType.AA))for(int i=0;i<lines.length;i++)text(c,lines[i],x,y+i*spacing,size,color,face);
        return y+(lines.length-1)*spacing;
    }
    private void hit(float x,float y,float w,float h,String description,Runnable action){if(capturing)return;RectF b=new RectF(x+pageShift,y+hitOffset,x+w+pageShift,y+h+hitOffset);if(contentClip&&!b.intersect(0,0,400,clipBottom))return;hits.add(new Hit(b,description,action));}
    private void read(float x,float y,float w,float h,String description){hit(x,y,w,h,description,null);}
    private static final class Hit{final RectF bounds;final String label;final Runnable action;Hit(RectF b,String s,Runnable a){bounds=b;label=s;action=a;}}
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            boolean moving=!scroller.isFinished();stopScroll();activePointer=event.getPointerId(0);velocity=VelocityTracker.obtain();velocity.addMovement(event);
            float x=event.getX()/scale,y=event.getY()/scale;downX=x;downY=lastY=y;dragging=moving;
            scrollingGesture=modal.isEmpty()&&maxScroll>0&&y<clipBottom;
            sheetGesture=!modal.isEmpty()&&!sheetClosing&&y>=sheetTop()&&y<=sheetTop()+110;
            clearPress();if(!moving&&!sheetClosing){for(int i=hits.size()-1;i>=0;i--){Hit hit=hits.get(i);if(hit.action!=null&&hit.bounds.contains(x,y)){pressedBounds=new RectF(hit.bounds);pressAt=now();break;}}}
            if(screen.equals("duel")||screen.equals("game"))hideKeyboardIfOutside(event);
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
    // Touches that reach the canvas missed every text field: release the keyboard on the setup page.
    private void hideKeyboardIfOutside(MotionEvent event){if(screen.equals("duel")&&(nameOne.hasFocus()||nameTwo.hasFocus()))hideKeyboard();}
    @Override public boolean performClick(){super.performClick();if(tappedAction!=null){Runnable action=tappedAction;tappedAction=null;action.run();invalidate();return true;}return false;}

    private void accessEvent(int id,int type){AccessibilityEvent event=AccessibilityEvent.obtain(type);event.setSource(this,id);event.setPackageName(activity.getPackageName());if(id>0&&id<=hits.size())event.setContentDescription(hits.get(id-1).label);if(getParent()!=null)getParent().requestSendAccessibilityEvent(this,event);}
    @Override public boolean dispatchHoverEvent(MotionEvent event){android.view.accessibility.AccessibilityManager manager=(android.view.accessibility.AccessibilityManager)activity.getSystemService(Context.ACCESSIBILITY_SERVICE);if(manager.isEnabled()&&manager.isTouchExplorationEnabled()){int target=-1;if(event.getAction()!=MotionEvent.ACTION_HOVER_EXIT){float x=event.getX()/scale,y=event.getY()/scale;for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).bounds.contains(x,y)){target=i+1;break;}}if(target!=hovered){if(hovered>0)accessEvent(hovered,AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);hovered=target;if(target>0)accessEvent(target,AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);}return target>0||super.dispatchHoverEvent(event);}return super.dispatchHoverEvent(event);}

    /** Virtual native controls keep the canvas navigable with TalkBack and switches. */
    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider(){return new AccessibilityNodeProvider(){
        @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id){
            if(id==View.NO_ID){AccessibilityNodeInfo node=AccessibilityNodeInfo.obtain(TopperiaView.this);onInitializeAccessibilityNodeInfo(node);for(int i=0;i<hits.size();i++)if(hits.get(i).bounds.bottom>0&&hits.get(i).bounds.top<H)node.addChild(TopperiaView.this,i+1);for(View child:new View[]{input,submit,nameOne,nameTwo})if(child.getVisibility()==VISIBLE)node.addChild(child);if(modal.isEmpty()&&maxScroll>0){node.setScrollable(true);node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}return node;}
            if(id<1||id>hits.size())return null;Hit hit=hits.get(id-1);AccessibilityNodeInfo node=AccessibilityNodeInfo.obtain();node.setSource(TopperiaView.this,id);node.setParent(TopperiaView.this);node.setPackageName(activity.getPackageName());node.setClassName(hit.action!=null?"android.widget.Button":"android.widget.TextView");node.setContentDescription(hit.label);node.setEnabled(true);node.setVisibleToUser(hit.bounds.bottom>0&&hit.bounds.top<H);node.setClickable(hit.action!=null);node.setFocusable(true);node.setAccessibilityFocused(accessibilityFocus==id);if(hit.action!=null)node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);node.addAction(accessibilityFocus==id?AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS:AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);Rect b=new Rect((int)(hit.bounds.left*scale),(int)(hit.bounds.top*scale),(int)(hit.bounds.right*scale),(int)(hit.bounds.bottom*scale));node.setBoundsInParent(b);int[] location=new int[2];getLocationOnScreen(location);b.offset(location[0],location[1]);node.setBoundsInScreen(b);return node;
        }
        @Override public boolean performAction(int id,int action,Bundle args){if(id==View.NO_ID&&(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)){if(!modal.isEmpty()||maxScroll<=0)return false;stopScroll();float target=Math.max(0,Math.min(maxScroll,scroll+(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1)*H*.6f));if(reduced())scroll=target;else scroller.startScroll(0,Math.round(scroll*scale),0,Math.round((target-scroll)*scale),300);postInvalidateOnAnimation();sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED);return true;}if(id>0&&id<=hits.size()){if(action==AccessibilityNodeInfo.ACTION_CLICK&&!sheetClosing&&hits.get(id-1).action!=null){hits.get(id-1).action.run();invalidate();return true;}if(action==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS){accessibilityFocus=id;accessEvent(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);return true;}if(action==AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS){accessibilityFocus=-1;accessEvent(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);return true;}}return false;}
    };}
}
