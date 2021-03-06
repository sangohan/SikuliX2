/*
 * Copyright 2010-2014, Sikuli.org, Sikulix.com
 * Released under the MIT License.
 *
 * modified RaiMan
 */
package org.sikuli.script;

import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.sikulix.util.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.sikulix.core.SX;
import com.sikulix.core.Visual;
import org.sikuli.util.Debug;
import org.sikuli.util.FindFailedDialog;
import org.sikuli.util.visual.ScreenHighlighter;

/**
 * A Region is a rectengular area and lies always completely inside its parent screen
 *
 */
public class Region {

  static RunTime runTime = RunTime.getRunTime();

  //<editor-fold defaultstate="collapsed" desc="logging">
  private static final int lvl = 3;
  private static final Logger logger = LogManager.getLogger("Settings.Region");

  private static void log(int level, String message, Object... args) {
    if (Debug.is(lvl) || level < 0) {
      message = String.format(message, args).replaceFirst("\\n", "\n          ");
      if (level == lvl) {
        logger.debug(message, args);
      } else if (level > lvl) {
        logger.trace(message, args);
      } else if (level == -1) {
        logger.error(message, args);
      } else {
        logger.info(message, args);
      }
    }
  }

  private static void logp(String message, Object... args) {
    System.out.println(String.format(message, args));
  }

  public static void terminate(int retval, String message, Object... args) {
    logger.fatal(String.format(" *** terminating: " + message, args));
    System.exit(retval);
  }
  
  private long started = 0;
  
  private void start() {
    started = new Date().getTime();
  }

  private long end() {
    return end("");
  }

  private long end(String message) {
    long ended = new Date().getTime();
    long diff = ended - started;
    if (!message.isEmpty()) {
      logp("[time] %s: %d msec", message, diff);
    }
    started = ended;
    return diff;
  }
//</editor-fold>

  /**
   * The Screen containing the Region
   */
  private IScreen scr;
  protected boolean otherScreen = false;

  /**
   * The ScreenHighlighter for this Region
   */
  private ScreenHighlighter overlay = null;
  /**
   * X-coordinate of the Region
   */
  public int x;
  /**
   * Y-coordinate of the Region
   */
  public int y;
  /**
   * Width of the Region
   */
  public int w;
  /**
   * Height of the Region
   */
  public int h;
  /**
   * Setting, how to react if an image is not found {@link FindFailed}
   */
  private FindFailedResponse findFailedResponse = null;
  /**
   * Setting {@link com.sikulix.util.Settings}, if exception is thrown if an image is not found
   */
  private boolean throwException = com.sikulix.util.Settings.ThrowException;
  /**
   * Default time to wait for an image {@link Settings}
   */
  private double autoWaitTimeout = Settings.AutoWaitTimeout;
  private float waitScanRate = Settings.WaitScanRate;
  /**
   * Flag, if an observer is running on this region {@link Settings}
   */
  private boolean observing = false;
  private float observeScanRate = Settings.ObserveScanRate;
  private int repeatWaitTime = Settings.RepeatWaitTime;
  /**
   * The {@link Observer} Singleton instance
   */
  private Observer regionObserver = null;

  /**
   * The last found {@link Match} in the Region
   */
  private Match lastMatch = null;
  /**
   * The last found {@link Match}es in the Region
   */
  private Iterator<Match> lastMatches = null;
  private long lastSearchTime = -1;
  private long lastFindTime = -1;
  private boolean isScreenUnion = false;
  private boolean isVirtual = false;
  private long lastSearchTimeRepeat = -1;

  /**
   * in case of not found the total wait time
   *
   * @return the duration of the last find op
   */
  public long getLastTime() {
    return lastFindTime;
  }

  /**
   * the area constants for use with get()
   */
  public static final int NW = 300, NORTH_WEST = NW, TL = NW;
  public static final int NM = 301, NORTH_MID = NM, TM = NM;
  public static final int NE = 302, NORTH_EAST = NE, TR = NE;
  public static final int EM = 312, EAST_MID = EM, RM = EM;
  public static final int SE = 322, SOUTH_EAST = SE, BR = SE;
  public static final int SM = 321, SOUTH_MID = SM, BM = SM;
  public static final int SW = 320, SOUTH_WEST = SW, BL = SW;
  public static final int WM = 310, WEST_MID = WM, LM = WM;
  public static final int MM = 311, MIDDLE = MM, M3 = MM;
  public static final int TT = 200;
  public static final int RR = 201;
  public static final int BB = 211;
  public static final int LL = 210;
  public static final int NH = 202, NORTH = NH, TH = NH;
  public static final int EH = 221, EAST = EH, RH = EH;
  public static final int SH = 212, SOUTH = SH, BH = SH;
  public static final int WH = 220, WEST = WH, LH = WH;
  public static final int MV = 441, MID_VERTICAL = MV, CV = MV;
  public static final int MH = 414, MID_HORIZONTAL = MH, CH = MH;
  public static final int M2 = 444, MIDDLE_BIG = M2, C2 = M2;
  public static final int EN = NE, EAST_NORTH = NE, RT = TR;
  public static final int ES = SE, EAST_SOUTH = SE, RB = BR;
  public static final int WN = NW, WEST_NORTH = NW, LT = TL;
  public static final int WS = SW, WEST_SOUTH = SW, LB = BL;

  /**
   * to support a raster over the region
   */
  private int rows;
  private int cols = 0;
  private int rowH = 0;
  private int colW = 0;
  private int rowHd = 0;
  private int colWd = 0;

  /**
   * {@inheritDoc}
   *
   * @return the description
   */
  @Override
  public String toString() {
    return String.format("R[%d,%d %dx%d]@S(%s)", x, y, w, h, 
            (getScreen() == null ? "?" : getScreen().getID()));
  }

  public String toJSON() {
    return String.format("[\"R\", [%d, %d, %d, %d]]", x, y, w, h);
  }

  //<editor-fold defaultstate="collapsed" desc="OFF: Specials for scripting environment">
  /*
   public Object __enter__() {
   Debug.error("Region: with(__enter__): Trying to make it a Jython Region for with: usage");
   IScriptRunner runner = Settings.getScriptRunner("jython", null, null);
   if (runner != null) {
   Object[] jyreg = new Object[]{this};
   if (runner.doSomethingSpecial("createRegionForWith", jyreg)) {
   if (jyreg[0] != null) {
   return jyreg[0];
   }
   }
   }
   Debug.error("Region: with(__enter__): Sorry, not possible");
   return null;
   }

   public void __exit__(Object type, Object value, Object traceback) {
   Debug.error("Region: with(__exit__): Sorry, not a Jython Region and not posssible!");
   }
   */
  //</editor-fold>
  //<editor-fold defaultstate="collapsed" desc="Initialization">
  /**
   * Detects on which Screen the Region is present. The region is cropped to the intersection with the given screen or
   * the screen with the largest intersection
   *
   * @param iscr The Screen containing the Region
   */
  public void initScreen(IScreen iscr) {
    // check given screen first
    Rectangle rect, screenRect;
    IScreen screen, screenOn;
    if (iscr != null) {
      if (iscr.isOtherScreen()) {
        if (x < 0) {
          w = w + x;
          x = 0;
        }
        if (y < 0) {
          h = h + y;
          y = 0;
        }
        this.scr = iscr;
        this.otherScreen = true;
        return;
      }
      if (iscr.getID() > -1) {
        rect = regionOnScreen(iscr);
        if (rect != null) {
          x = rect.x;
          y = rect.y;
          w = rect.width;
          h = rect.height;
          this.scr = iscr;
          return;
        }
      } else {
        // is ScreenUnion
        return;
      }
    }
    // check all possible screens if no screen was given or the region is not on given screen
    // crop to the screen with the largest intersection
    screenRect = new Rectangle(0, 0, 0, 0);
    screenOn = null;
    for (int i = 0; i < Screen.getNumberScreens(); i++) {
      screen = Screen.getScreen(i);
      rect = regionOnScreen(screen);
      if (rect != null) {
        if (rect.width * rect.height > screenRect.width * screenRect.height) {
          screenRect = rect;
          screenOn = screen;
        }
      }
    }
    if (screenOn != null) {
      x = screenRect.x;
      y = screenRect.y;
      w = screenRect.width;
      h = screenRect.height;
      this.scr = screenOn;
    } else {
      // no screen found
      this.scr = null;
      Debug.error("Region(%d,%d,%d,%d) outside any screen - subsequent actions might not work as expected", x, y, w, h);
    }
  }

  private Location checkAndSetRemote(Location loc) {
    if (!isOtherScreen()) {
      return loc;
    }
    return loc.setOtherScreen(scr);
  }

  public static Region virtual(Rectangle rect) {
    Region reg = new Region();
    reg.x = rect.x;
    reg.y = rect.y;
    reg.w = rect.width;
    reg.h = rect.height;
    reg.setVirtual(true);
    reg.scr = Screen.getPrimaryScreen();
    return reg;
  }

  /**
   * INTERNAL USE - EXPERIMENTAL if true: this region is not bound to any screen
   *
   * @return the current state
   */
  public boolean isVirtual() {
    return isVirtual;
  }

  /**
   * INTERNAL USE - EXPERIMENTAL
   *
   * @param state if true: this region is not bound to any screen
   */
  public void setVirtual(boolean state) {
    isVirtual = state;
  }

  /**
   * INTERNAL USE: checks wether this region belongs to a non-Desktop screen
   *
   * @return true/false
   */
  public boolean isOtherScreen() {
    return otherScreen;
  }

  /**
   * INTERNAL USE: flags this region as belonging to a non-Desktop screen
   */
  public void setOtherScreen() {
    otherScreen = true;
  }

  /**
   * Checks if the Screen contains the Region.
   *
   * @param screen The Screen in which the Region might be
   * @return True, if the Region is on the Screen. False if the Region is not inside the Screen
   */
  protected Rectangle regionOnScreen(IScreen screen) {
    if (screen == null) {
      return null;
    }
    // get intersection of Region and Screen
    Rectangle rect = screen.getRect().intersection(getRect());
    // no Intersection, Region is not on the Screen
    if (rect.isEmpty()) {
      return null;
    }
    return rect;
  }

  /**
   * Check wether thie Region is contained by any of the available screens
   *
   * @return true if yes, false otherwise
   */
  public boolean isValid() {
    return scr != null && w != 0 && h != 0;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Constructors to be used with Jython">
  /**
   * Create a region with the provided coordinate / size and screen
   *
   * @param X X position
   * @param Y Y position
   * @param W width
   * @param H heigth
   * @param screenNumber The number of the screen containing the Region
   */
  public Region(int X, int Y, int W, int H, int screenNumber) {
    this(X, Y, W, H, Screen.getScreen(screenNumber));
    this.rows = 0;
  }

  /**
   * Create a region with the provided coordinate / size and screen
   *
   * @param X X position
   * @param Y Y position
   * @param W width
   * @param H heigth
   * @param parentScreen the screen containing the Region
   */
  public Region(int X, int Y, int W, int H, IScreen parentScreen) {
    this.rows = 0;
    this.x = X;
    this.y = Y;
    this.w = W > 1 ? W : 1;
    this.h = H > 1 ? H : 1;
    initScreen(parentScreen);
  }

  /**
   * Create a region with the provided coordinate / size
   *
   * @param X X position
   * @param Y Y position
   * @param W width
   * @param H heigth
   */
  public Region(int X, int Y, int W, int H) {
    this(X, Y, W, H, null);
    this.rows = 0;
  }

  /**
   * Create a region from a Rectangle
   *
   * @param r the Rectangle
   */
  public Region(Rectangle r) {
    this(r.x, r.y, r.width, r.height, null);
    this.rows = 0;
  }

  /**
   * Create a new region from another region<br>including the region's settings
   *
   * @param r the region
   */
  public Region(Region r) {
    init(r);
  }

  public void init(Region r) {
    if (!r.isValid()) {
      return;
    }
    x = r.x;
    y = r.y;
    w = r.w;
    h = r.h;
    scr = r.getScreen();
    otherScreen = r.isOtherScreen();
    rows = 0;
    autoWaitTimeout = r.autoWaitTimeout;
    findFailedResponse = r.findFailedResponse;
    throwException = r.throwException;
    waitScanRate = r.waitScanRate;
    observeScanRate = r.observeScanRate;
    repeatWaitTime = r.repeatWaitTime;
  }

  //</editor-fold>
  //<editor-fold defaultstate="collapsed" desc="Quasi-Constructors to be used in Java">
  /**
   * internal use only, used for new Screen objects to get the Region behavior
   */
  protected Region() {
    this.rows = 0;
  }

  /**
   * internal use only, used for new Screen objects to get the Region behavior
   */
  protected Region(boolean isScreenUnion) {
    this.isScreenUnion = isScreenUnion;
    this.rows = 0;
  }

  /**
   * Create a region with the provided top left corner and size
   *
   * @param X top left X position
   * @param Y top left Y position
   * @param W width
   * @param H heigth
   * @return then new region
   */
  public static Region create(int X, int Y, int W, int H) {
    return Region.create(X, Y, W, H, null);
  }

  /**
   * Create a region with the provided top left corner and size
   *
   * @param X top left X position
   * @param Y top left Y position
   * @param W width
   * @param H heigth
   * @param scr the source screen
   * @return the new region
   */
  private static Region create(int X, int Y, int W, int H, IScreen scr) {
    return new Region(X, Y, W, H, scr);
  }

  /**
   * Create a region with the provided top left corner and size
   *
   * @param loc top left corner
   * @param w width
   * @param h height
   * @return then new region
   */
  public static Region create(Location loc, int w, int h) {
    return Region.create(loc.x, loc.y, w, h, loc.getScreen());
  }
  /**
   * Flag for the {@link #create(Location, int, int, int, int)} method. Sets the Location to be on the left corner of
   * the new Region.
   */
  public final static int CREATE_X_DIRECTION_LEFT = 0;
  /**
   * Flag for the {@link #create(Location, int, int, int, int)} method. Sets the Location to be on the right corner of
   * the new Region.
   */
  public final static int CREATE_X_DIRECTION_RIGHT = 1;
  /**
   * Flag for the {@link #create(Location, int, int, int, int)} method. Sets the Location to be on the top corner of the
   * new Region.
   */
  public final static int CREATE_Y_DIRECTION_TOP = 0;
  /**
   * Flag for the {@link #create(Location, int, int, int, int)} method. Sets the Location to be on the bottom corner of
   * the new Region.
   */
  public final static int CREATE_Y_DIRECTION_BOTTOM = 1;

  /**
   * create a region with a corner at the given point<br>as specified with x y<br> 0 0 top left<br> 0 1 bottom left<br>
   * 1 0 top right<br> 1 1 bottom right<br>
   *
   * @param loc the refence point
   * @param create_x_direction == 0 is left side !=0 is right side
   * @param create_y_direction == 0 is top side !=0 is bottom side
   * @param w the width
   * @param h the height
   * @return the new region
   */
  public static Region create(Location loc, int create_x_direction, int create_y_direction, int w, int h) {
    int X;
    int Y;
    int W = w;
    int H = h;
    if (create_x_direction == CREATE_X_DIRECTION_LEFT) {
      if (create_y_direction == CREATE_Y_DIRECTION_TOP) {
        X = loc.x;
        Y = loc.y;
      } else {
        X = loc.x;
        Y = loc.y - h;
      }
    } else {
      if (create_y_direction == CREATE_Y_DIRECTION_TOP) {
        X = loc.x - w;
        Y = loc.y;
      } else {
        X = loc.x - w;
        Y = loc.y - h;
      }
    }
    return Region.create(X, Y, W, H, loc.getScreen());
  }

  /**
   * create a region with a corner at the given point<br>as specified with x y<br> 0 0 top left<br> 0 1 bottom left<br>
   * 1 0 top right<br> 1 1 bottom right<br>same as the corresponding create method, here to be naming compatible with
   * class Location
   *
   * @param loc the refence point
   * @param x ==0 is left side !=0 is right side
   * @param y ==0 is top side !=0 is bottom side
   * @param w the width
   * @param h the height
   * @return the new region
   */
  public static Region grow(Location loc, int x, int y, int w, int h) {
    return Region.create(loc, x, y, w, h);
  }

  /**
   * Create a region from a Rectangle
   *
   * @param r the Rectangle
   * @return the new region
   */
  public static Region create(Rectangle r) {
    return Region.create(r.x, r.y, r.width, r.height, null);
  }

  /**
   * Create a region from a Rectangle on a given Screen
   *
   * @param r the Rectangle
   * @param parentScreen the new parent screen
   * @return the new region
   */
  protected static Region create(Rectangle r, IScreen parentScreen) {
    return Region.create(r.x, r.y, r.width, r.height, parentScreen);
  }

  /**
   * Create a region from another region<br>including the region's settings
   *
   * @param r the region
   * @return then new region
   */
  public static Region create(Region r) {
    Region reg = Region.create(r.x, r.y, r.w, r.h, r.getScreen());
    reg.autoWaitTimeout = r.autoWaitTimeout;
    reg.findFailedResponse = r.findFailedResponse;
    reg.throwException = r.throwException;
    return reg;
  }

  /**
   * create a region with the given point as center and the given size
   *
   * @param loc the center point
   * @param w the width
   * @param h the height
   * @return the new region
   */
  public static Region grow(Location loc, int w, int h) {
    int X = loc.x - (int) w / 2;
    int Y = loc.y - (int) h / 2;
    return Region.create(X, Y, w, h, loc.getScreen());
  }

  /**
   * create a minimal region at given point with size 1 x 1
   *
   * @param loc the point
   * @return the new region
   */
  public static Region grow(Location loc) {
    return Region.create(loc.x, loc.y, 1, 1, loc.getScreen());
  }

  //</editor-fold>
  //<editor-fold defaultstate="collapsed" desc="handle coordinates">
  /**
   * check if current region contains given point
   *
   * @param point Point
   * @return true/false
   */
  public boolean contains(Location point) {
    return getRect().contains(point.x, point.y);
  }

  /**
   * check if mouse pointer is inside current region
   *
   * @return true/false
   */
  public boolean containsMouse() {
    return contains(Mouse.at());
  }

  /**
   * new region with same offset to current screen's top left on given screen
   *
   * @param scrID number of screen
   * @return new region
   */
  public Region copyTo(int scrID) {
    return copyTo(Screen.getScreen(scrID));
  }

  /**
   * new region with same offset to current screen's top left on given screen
   *
   * @param screen new parent screen
   * @return new region
   */
  public Region copyTo(IScreen screen) {
    Location o = new Location(getScreen().getBounds().getLocation());
    Location n = new Location(screen.getBounds().getLocation());
    return Region.create(n.x + x - o.x, n.y + y - o.y, w, h, screen);
  }

  /**
   * used in Observer.callChangeObserving, Finder.next to adjust region relative coordinates of matches to screen
   * coordinates
   *
   * @param m
   * @return the modified match
   */
  protected Match toGlobalCoord(Match m) {
    m.x += x;
    m.y += y;
    return m;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="handle Settings">
  //TODO should be possible to reset to current global value resetXXX()
  /**
   * true - (initial setting) should throw exception FindFailed if findX unsuccessful in this region<br> false - do not
   * abort script on FindFailed (might leed to null pointer exceptions later)
   *
   * @param flag true/false
   */
  public void setThrowException(boolean flag) {
    throwException = flag;
    if (throwException) {
      findFailedResponse = FindFailedResponse.ABORT;
    } else {
      findFailedResponse = FindFailedResponse.SKIP;
    }
  }

  /**
   * current setting for this region (see setThrowException)
   *
   * @return true/false
   */
  public boolean getThrowException() {
    return throwException;
  }

  /**
   * the time in seconds a find operation should wait for the appearence of the target in this region<br> initial value
   * is the global AutoWaitTimeout setting at time of Region creation
   *
   * @param sec seconds
   */
  public void setAutoWaitTimeout(double sec) {
    autoWaitTimeout = sec;
  }

  /**
   * current setting for this region (see setAutoWaitTimeout)
   *
   * @return value of seconds
   */
  public double getAutoWaitTimeout() {
    return autoWaitTimeout;
  }

  /**
   * FindFailedResponse.<br> ABORT - (initial value) abort script on FindFailed (= setThrowException(true) )<br> SKIP -
   * ignore FindFailed (same as setThrowException(false) )<br>
   * PROMPT - display prompt on FindFailed to let user decide how to proceed<br> RETRY - continue to wait for appearence
   * on FindFailed (caution: endless loop)
   *
   * @param response the FindFailedResponse
   */
  public void setFindFailedResponse(FindFailedResponse response) {
    findFailedResponse = response;
  }

  /**
   *
   * @return the current setting (see setFindFailedResponse)
   */
  public FindFailedResponse getFindFailedResponse() {
    return findFailedResponse;
  }

  /**
   *
   * @return the regions current WaitScanRate
   */
  public float getWaitScanRate() {
    return waitScanRate;
  }

  /**
   * set the regions individual WaitScanRate
   *
   * @param waitScanRate decimal number
   */
  public void setWaitScanRate(float waitScanRate) {
    this.waitScanRate = waitScanRate;
  }

  /**
   *
   * @return the regions current ObserveScanRate
   */
  public float getObserveScanRate() {
    return observeScanRate;
  }

  /**
   * set the regions individual ObserveScanRate
   *
   * @param observeScanRate decimal number
   */
  public void setObserveScanRate(float observeScanRate) {
    this.observeScanRate = observeScanRate;
  }

  /**
   * INTERNAL USE: Observe
   *
   * @return the regions current RepeatWaitTime time in seconds
   */
  public int getRepeatWaitTime() {
    return repeatWaitTime;
  }

  /**
   * INTERNAL USE: Observe set the regions individual WaitForVanish
   *
   * @param time in seconds
   */
  public void setRepeatWaitTime(int time) {
    repeatWaitTime = time;
  }

  //</editor-fold>
  //<editor-fold defaultstate="collapsed" desc="getters / setters / modificators">
  /**
   *
   * @return the Screen object containing the region
   */
  public IScreen getScreen() {
    return scr;
  }

  // to avoid NPE for Regions being outside any screen
  private IRobot getRobotForRegion() {
    if (getScreen() == null || isScreenUnion) {
      return Screen.getPrimaryScreen().getRobot();
    }
    return getScreen().getRobot();
  }

  /**
   *
   * @return the screen, that contains the top left corner of the region. Returns primary screen if outside of any
   * screen.
   * @deprecated Only for compatibility, to get the screen containing this region, use {@link #getScreen()}
   */
  @Deprecated
  public IScreen getScreenContaining() {
    return getScreen();
  }

  /**
   * Sets a new Screen for this region.
   *
   * @param scr the containing screen object
   * @return the region itself
   */
  protected Region setScreen(IScreen scr) {
    initScreen(scr);
    return this;
  }

  /**
   * Sets a new Screen for this region.
   *
   * @param id the containing screen object's id
   * @return the region itself
   */
  protected Region setScreen(int id) {
    return setScreen(Screen.getScreen(id));
  }

  /**
   * synonym for showMonitors
   */
  public void showScreens() {
    Screen.showMonitors();
  }

  /**
   * synonym for resetMonitors
   */
  public void resetScreens() {
    Screen.resetMonitors();
  }

  // ************************************************
  /**
   *
   * @return the center pixel location of the region
   */
  public Location getCenter() {
    return checkAndSetRemote(new Location(getX() + getW() / 2, getY() + getH() / 2));
  }

  /**
   * convenience method
   *
   * @return the region's center
   */
  public Location getTarget() {
    return getCenter();
  }

  /**
   * Moves the region to the area, whose center is the given location
   *
   * @param loc the location which is the new center of the region
   * @return the region itself
   */
  public Region setCenter(Location loc) {
    Location c = getCenter();
    x = x - c.x + loc.x;
    y = y - c.y + loc.y;
    initScreen(null);
    return this;
  }

  /**
   *
   * @return top left corner Location
   */
  public Location getTopLeft() {
    return checkAndSetRemote(new Location(x, y));
  }

  /**
   * Moves the region to the area, whose top left corner is the given location
   *
   * @param loc the location which is the new top left point of the region
   * @return the region itself
   */
  public Region setTopLeft(Location loc) {
    return setLocation(loc);
  }

  /**
   *
   * @return top right corner Location
   */
  public Location getTopRight() {
    return checkAndSetRemote(new Location(x + w - 1, y));
  }

  /**
   * Moves the region to the area, whose top right corner is the given location
   *
   * @param loc the location which is the new top right point of the region
   * @return the region itself
   */
  public Region setTopRight(Location loc) {
    Location c = getTopRight();
    x = x - c.x + loc.x;
    y = y - c.y + loc.y;
    initScreen(null);
    return this;
  }

  /**
   *
   * @return bottom left corner Location
   */
  public Location getBottomLeft() {
    return checkAndSetRemote(new Location(x, y + h - 1));
  }

  /**
   * Moves the region to the area, whose bottom left corner is the given location
   *
   * @param loc the location which is the new bottom left point of the region
   * @return the region itself
   */
  public Region setBottomLeft(Location loc) {
    Location c = getBottomLeft();
    x = x - c.x + loc.x;
    y = y - c.y + loc.y;
    initScreen(null);
    return this;
  }

  /**
   *
   * @return bottom right corner Location
   */
  public Location getBottomRight() {
    return checkAndSetRemote(new Location(x + w - 1, y + h - 1));
  }

  /**
   * Moves the region to the area, whose bottom right corner is the given location
   *
   * @param loc the location which is the new bottom right point of the region
   * @return the region itself
   */
  public Region setBottomRight(Location loc) {
    Location c = getBottomRight();
    x = x - c.x + loc.x;
    y = y - c.y + loc.y;
    initScreen(null);
    return this;
  }

  // ************************************************
  /**
   *
   * @return x of top left corner
   */
  public int getX() {
    return x;
  }

  /**
   *
   * @return y of top left corner
   */
  public int getY() {
    return y;
  }

  /**
   *
   * @return width of region
   */
  public int getW() {
    return w;
  }

  /**
   *
   * @return height of region
   */
  public int getH() {
    return h;
  }

  /**
   *
   * @param X new x position of top left corner
   */
  public void setX(int X) {
    x = X;
    initScreen(null);
  }

  /**
   *
   * @param Y new y position of top left corner
   */
  public void setY(int Y) {
    y = Y;
    initScreen(null);
  }

  /**
   *
   * @param W new width
   */
  public void setW(int W) {
    w = W > 1 ? W : 1;
    initScreen(null);
  }

  /**
   *
   * @param H new height
   */
  public void setH(int H) {
    h = H > 1 ? H : 1;
    initScreen(null);
  }

  // ************************************************
  /**
   *
   * @param W new width
   * @param H new height
   * @return the region itself
   */
  public Region setSize(int W, int H) {
    w = W > 1 ? W : 1;
    h = H > 1 ? H : 1;
    initScreen(null);
    return this;
  }

  /**
   *
   * @return the AWT Rectangle of the region
   */
  public Rectangle getRect() {
    return new Rectangle(x, y, w, h);
  }

  /**
   * set the regions position/size<br>this might move the region even to another screen
   *
   * @param r the AWT Rectangle to use for position/size
   * @return the region itself
   */
  public Region setRect(Rectangle r) {
    return setRect(r.x, r.y, r.width, r.height);
  }

  /**
   * set the regions position/size<br>this might move the region even to another screen
   *
   * @param X new x of top left corner
   * @param Y new y of top left corner
   * @param W new width
   * @param H new height
   * @return the region itself
   */
  public Region setRect(int X, int Y, int W, int H) {
    x = X;
    y = Y;
    w = W > 1 ? W : 1;
    h = H > 1 ? H : 1;
    initScreen(null);
    return this;
  }

  /**
   * set the regions position/size<br>this might move the region even to another screen
   *
   * @param r the region to use for position/size
   * @return the region itself
   */
  public Region setRect(Region r) {
    return setRect(r.x, r.y, r.w, r.h);
  }

  // ****************************************************
  /**
   * resets this region (usually a Screen object) to the coordinates of the containing screen
   *
   * Because of the wanted side effect for the containing screen, this should only be used with screen objects. For
   * Region objects use setRect() instead.
   */
  public void setROI() {
    setROI(getScreen().getBounds());
  }

  /**
   * resets this region to the given location, and size <br> this might move the region even to another screen
   *
   * <br>Because of the wanted side effect for the containing screen, this should only be used with screen objects.
   * <br>For Region objects use setRect() instead.
   *
   * @param X new x
   * @param Y new y
   * @param W new width
   * @param H new height
   */
  public void setROI(int X, int Y, int W, int H) {
    x = X;
    y = Y;
    w = W > 1 ? W : 1;
    h = H > 1 ? H : 1;
    initScreen(null);
  }

  /**
   * resets this region to the given rectangle <br> this might move the region even to another screen
   *
   * <br>Because of the wanted side effect for the containing screen, this should only be used with screen objects.
   * <br>For Region objects use setRect() instead.
   *
   * @param r AWT Rectangle
   */
  public void setROI(Rectangle r) {
    setROI(r.x, r.y, r.width, r.height);
  }

  /**
   * resets this region to the given region <br> this might move the region even to another screen
   *
   * <br>Because of the wanted side effect for the containing screen, this should only be used with screen objects.
   * <br>For Region objects use setRect() instead.
   *
   * @param reg Region
   */
  public void setROI(Region reg) {
    setROI(reg.getX(), reg.getY(), reg.getW(), reg.getH());
  }

  /**
   * A function only for backward compatibility - Only makes sense with Screen objects
   *
   * @return the Region being the current ROI of the containing Screen
   */
  public Region getROI() {
    return new Region(getScreen().getRect());
  }

  // ****************************************************
  /**
   *
   * @return the region itself
   * @deprecated only for backward compatibility
   */
  @Deprecated
  public Region inside() {
    return this;
  }

  /**
   * set the regions position<br>this might move the region even to another screen
   *
   * @param loc new top left corner
   * @return the region itself
   * @deprecated to be like AWT Rectangle API use setLocation()
   */
  @Deprecated
  public Region moveTo(Location loc) {
    return setLocation(loc);
  }

  /**
   * set the regions position<br>this might move the region even to another screen
   *
   * @param loc new top left corner
   * @return the region itself
   */
  public Region setLocation(Location loc) {
    x = loc.x;
    y = loc.y;
    initScreen(null);
    return this;
  }

  /**
   * set the regions position/size<br>this might move the region even to another screen
   *
   * @param r Region
   * @return the region itself
   * @deprecated to be like AWT Rectangle API use setRect() instead
   */
  @Deprecated
  public Region morphTo(Region r) {
    return setRect(r);
  }

  /**
   * resize the region using the given padding values<br>might be negative
   *
   * @param l padding on left side
   * @param r padding on right side
   * @param t padding at top side
   * @param b padding at bottom side
   * @return the region itself
   */
  public Region add(int l, int r, int t, int b) {
    x = x - l;
    y = y - t;
    w = w + l + r;
    if (w < 1) {
      w = 1;
    }
    h = h + t + b;
    if (h < 1) {
      h = 1;
    }
    initScreen(null);
    return this;
  }

  /**
   * extend the region, so it contains the given region<br>but only the part inside the current screen
   *
   * @param r the region to include
   * @return the region itself
   */
  public Region add(Region r) {
    Rectangle rect = getRect();
    rect.add(r.getRect());
    setRect(rect);
    initScreen(null);
    return this;
  }

  /**
   * extend the region, so it contains the given point<br>but only the part inside the current screen
   *
   * @param loc the point to include
   * @return the region itself
   */
  public Region add(Location loc) {
    Rectangle rect = getRect();
    rect.add(loc.x, loc.y);
    setRect(rect);
    initScreen(null);
    return this;
  }

  // ************************************************
  /**
   * a find operation saves its match on success in the used region object<br>unchanged if not successful
   *
   * @return the Match object from last successful find in this region
   */
  public Match getLastMatch() {
    return lastMatch;
  }

  // ************************************************
  /**
   * a searchAll operation saves its matches on success in the used region object<br>unchanged if not successful
   *
   * @return a Match-Iterator of matches from last successful searchAll in this region
   */
  public Iterator<Match> getLastMatches() {
    return lastMatches;
  }

  // ************************************************
  /**
   * get the last image taken on this regions screen
   *
   * @return the stored ScreenImage
   */
  public ScreenImage getLastScreenImage() {
    return getScreen().getLastScreenImageFromScreen();
  }

  /**
   * stores the lastScreenImage in the current bundle path with a created unique name
   *
   * @return the absolute file name
   * @throws java.io.IOException if not possible
   */
  public String getLastScreenImageFile() throws IOException {
    return getScreen().getLastScreenImageFile(ImagePath.getBundlePath(), null);
  }

  /**
   * stores the lastScreenImage in the current bundle path with the given name
   *
   * @param name file name (.png is added if not there)
   * @return the absolute file name
   * @throws java.io.IOException if not possible
   */
  public String getLastScreenImageFile(String name) throws IOException {
    return getScreen().getLastScreenImageFromScreen().getFile(ImagePath.getBundlePath(), name);
  }

  /**
   * stores the lastScreenImage in the given path with the given name
   *
   * @param path path to use
   * @param name file name (.png is added if not there)
   * @return the absolute file name
   * @throws java.io.IOException if not possible
   */
  public String getLastScreenImageFile(String path, String name) throws IOException {
    return getScreen().getLastScreenImageFromScreen().getFile(path, name);
  }

  //</editor-fold>
  //<editor-fold defaultstate="collapsed" desc="spatial operators - new regions">
  /**
   * check if current region contains given region
   *
   * @param region the other Region
   * @return true/false
   */
  public boolean contains(Region region) {
    return getRect().contains(region.getRect());
  }

  /**
   * create a Location object, that can be used as an offset taking the width and hight of this Region
   *
   * @return a new Location object with width and height as x and y
   */
  public Location asOffset() {
    return new Location(w, h);
  }

  /**
   * create region with same size at top left corner offset
   *
   * @param loc use its x and y to set the offset
   * @return the new region
   */
  public Region offset(Location loc) {
    return Region.create(x + loc.x, y + loc.y, w, h, scr);
  }

  /**
   * create region with same size at top left corner offset
   *
   * @param x horizontal offset
   * @param y vertical offset
   * @return the new region
   */
  public Region offset(int x, int y) {
    return Region.create(this.x + x, this.y + y, w, h, scr);
  }

  /**
   * create a region enlarged Settings.DefaultPadding pixels on each side
   *
   * @return the new region
   * @deprecated to be like AWT Rectangle API use grow() instead
   */
  @Deprecated
  public Region nearby() {
    return grow(Visual.getMargin()[0], Visual.getMargin()[1]);
  }

  /**
   * create a region enlarged range pixels on each side
   *
   * @param range the margin to be added around
   * @return the new region
   * @deprecated to be like AWT Rectangle API use grow() instaed
   */
  @Deprecated
  public Region nearby(int range) {
    return grow(range, range);
  }

  /**
   * create a region enlarged n pixels on each side (n = Settings.DefaultPadding = 50 default)
   *
   * @return the new region
   */
  public Region grow() {
    return grow(Visual.getMargin()[0], Visual.getMargin()[1]);
  }

  /**
   * create a region enlarged range pixels on each side
   *
   * @param range the margin to be added around
   * @return the new region
   */
  public Region grow(int range) {
    return grow(range, range);
  }

  /**
   * create a region enlarged w pixels on left and right side and h pixels at top and bottom
   *
   * @param w pixels horizontally
   * @param h pixels vertically
   * @return the new region
   */
  public Region grow(int w, int h) {
    Rectangle r = getRect();
    r.grow(w, h);
    return Region.create(r.x, r.y, r.width, r.height, scr);
  }

  /**
   * create a region enlarged l pixels on left and r pixels right side and t pixels at top side and b pixels a bottom
   * side. negative values go inside (shrink)
   *
   * @param l add to the left
   * @param r add to right
   * @param t add above
   * @param b add beneath
   * @return the new region
   */
  public Region grow(int l, int r, int t, int b) {
    return Region.create(x - l, y - t, w + l + r, h + t + b, scr);
  }

  /**
   * point middle on right edge
   *
   * @return point middle on right edge
   */
  public Location rightAt() {
    return rightAt(0);
  }

  /**
   * positive offset goes to the right. might be off current screen
   *
   * @param offset pixels
   * @return point with given offset horizontally to middle point on right edge
   */
  public Location rightAt(int offset) {
    return checkAndSetRemote(new Location(x + w + offset, y + h / 2));
  }

  /**
   * create a region right of the right side with same height. the new region extends to the right screen border<br>
   * use grow() to include the current region
   *
   * @return the new region
   */
  public Region right() {
    int distToRightScreenBorder = getScreen().getX() + getScreen().getW() - (getX() + getW());
    return right(distToRightScreenBorder);
  }

  /**
   * create a region right of the right side with same height and given width. negative width creates the right part
   * with width inside the region<br>
   * use grow() to include the current region
   *
   * @param width pixels
   * @return the new region
   */
  public Region right(int width) {
    int _x;
    if (width < 0) {
      _x = x + w + width;
    } else {
      _x = x + w;
    }
    return Region.create(_x, y, Math.abs(width), h, scr);
  }

  /**
   *
   * @return point middle on left edge
   */
  public Location leftAt() {
    return leftAt(0);
  }

  /**
   * negative offset goes to the left <br>might be off current screen
   *
   * @param offset pixels
   * @return point with given offset horizontally to middle point on left edge
   */
  public Location leftAt(int offset) {
    return checkAndSetRemote(new Location(x + offset, y + h / 2));
  }

  /**
   * create a region left of the left side with same height<br> the new region extends to the left screen border<br> use
   * grow() to include the current region
   *
   * @return the new region
   */
  public Region left() {
    int distToLeftScreenBorder = getX() - getScreen().getX();
    return left(distToLeftScreenBorder);
  }

  /**
   * create a region left of the left side with same height and given width<br>
   * negative width creates the left part with width inside the region use grow() to include the current region <br>
   *
   * @param width pixels
   * @return the new region
   */
  public Region left(int width) {
    int _x;
    if (width < 0) {
      _x = x;
    } else {
      _x = x - width;
    }
    return Region.create(getScreen().getBounds().intersection(new Rectangle(_x, y, Math.abs(width), h)), scr);
  }

  /**
   *
   * @return point middle on top edge
   */
  public Location aboveAt() {
    return aboveAt(0);
  }

  /**
   * negative offset goes towards top of screen <br>might be off current screen
   *
   * @param offset pixels
   * @return point with given offset vertically to middle point on top edge
   */
  public Location aboveAt(int offset) {
    return checkAndSetRemote(new Location(x + w / 2, y + offset));
  }

  /**
   * create a region above the top side with same width<br> the new region extends to the top screen border<br> use
   * grow() to include the current region
   *
   * @return the new region
   */
  public Region above() {
    int distToAboveScreenBorder = getY() - getScreen().getY();
    return above(distToAboveScreenBorder);
  }

  /**
   * create a region above the top side with same width and given height<br>
   * negative height creates the top part with height inside the region use grow() to include the current region
   *
   * @param height pixels
   * @return the new region
   */
  public Region above(int height) {
    int _y;
    if (height < 0) {
      _y = y;
    } else {
      _y = y - height;
    }
    return Region.create(getScreen().getBounds().intersection(new Rectangle(x, _y, w, Math.abs(height))), scr);
  }

  /**
   *
   * @return point middle on bottom edge
   */
  public Location belowAt() {
    return belowAt(0);
  }

  /**
   * positive offset goes towards bottom of screen <br>might be off current screen
   *
   * @param offset pixels
   * @return point with given offset vertically to middle point on bottom edge
   */
  public Location belowAt(int offset) {
    return checkAndSetRemote(new Location(x + w / 2, y + h - offset));
  }

  /**
   * create a region below the bottom side with same width<br> the new region extends to the bottom screen border<br>
   * use grow() to include the current region
   *
   * @return the new region
   */
  public Region below() {
    int distToBelowScreenBorder = getScreen().getY() + getScreen().getH() - (getY() + getH());
    return below(distToBelowScreenBorder);
  }

  /**
   * create a region below the bottom side with same width and given height<br>
   * negative height creates the bottom part with height inside the region use grow() to include the current region
   *
   * @param height pixels
   * @return the new region
   */
  public Region below(int height) {
    int _y;
    if (height < 0) {
      _y = y + h + height;
    } else {
      _y = y + h;
    }
    return Region.create(x, _y, w, Math.abs(height), scr);
  }

  /**
   * create a new region containing both regions
   *
   * @param ur region to unite with
   * @return the new region
   */
  public Region union(Region ur) {
    Rectangle r = getRect().union(ur.getRect());
    return Region.create(r.x, r.y, r.width, r.height, scr);
  }

  /**
   * create a region that is the intersection of the given regions
   *
   * @param ir the region to intersect with like AWT Rectangle API
   * @return the new region
   */
  public Region intersection(Region ir) {
    Rectangle r = getRect().intersection(ir.getRect());
    return Region.create(r.x, r.y, r.width, r.height, scr);
  }

  //</editor-fold>
  //<editor-fold defaultstate="collapsed" desc="parts of a Region">
  /**
   * select the specified part of the region.
   *
   * <br>Constants for the top parts of a region (Usage: Region.CONSTANT)<br>
   * shown in brackets: possible shortcuts for the part constant<br>
   * NORTH (NH, TH) - upper half <br>
   * NORTH_WEST (NW, TL) - left third in upper third <br>
   * NORTH_MID (NM, TM) - middle third in upper third <br>
   * NORTH_EAST (NE, TR) - right third in upper third <br>
   * ... similar for the other directions: <br>
   * right side: EAST (Ex, Rx)<br>
   * bottom part: SOUTH (Sx, Bx) <br>
   * left side: WEST (Wx, Lx)<br>
   * <br>
   * specials for quartered:<br>
   * TT top left quarter<br>
   * RR top right quarter<br>
   * BB bottom right quarter<br>
   * LL bottom left quarter<br>
   * <br>
   * specials for the center parts:<br>
   * MID_VERTICAL (MV, CV) half of width vertically centered <br>
   * MID_HORIZONTAL (MH, CH) half of height horizontally centered <br>
   * MID_BIG (M2, C2) half of width / half of height centered <br>
   * MID_THIRD (MM, CC) third of width / third of height centered <br>
   * <br>
   * Based on the scheme behind these constants there is another possible usage:<br>
   * specify part as e 3 digit integer where the digits xyz have the following meaning<br>
   * 1st x: use a raster of x rows and x columns<br>
   * 2nd y: the row number of the wanted cell<br>
   * 3rd z: the column number of the wanted cell<br>
   * y and z are counting from 0<br>
   * valid numbers: 200 up to 999 (&lt; 200 are invalid and return the region itself) <br>
   * example: get(522) will use a raster of 5 rows and 5 columns and return the cell in the middle<br>
   * special cases:<br>
   * if either y or z are == or &gt; x: returns the respective row or column<br>
   * example: get(525) will use a raster of 5 rows and 5 columns and return the row in the middle<br>
   * <br>
   * internally this is based on {@link #setRaster(int, int) setRaster} and {@link #getCell(int, int) getCell} <br>
   * <br>
   * If you need only one row in one column with x rows or only one column in one row with x columns you can use
   * {@link #getRow(int, int) getRow} or {@link #getCol(int, int) getCol}
   *
   * @param part the part to get (Region.PART long or short)
   * @return new region
   */
  public Region get(int part) {
    return Region.create(getRectangle(getRect(), part));
  }

  protected static Rectangle getRectangle(Rectangle rect, int part) {
    if (part < 200 || part > 999) {
      return rect;
    }
    Region r = Region.create(rect);
    int pTyp = (int) (part / 100);
    int pPos = part - pTyp * 100;
    int pRow = (int) (pPos / 10);
    int pCol = pPos - pRow * 10;
    r.setRaster(pTyp, pTyp);
    if (pTyp == 3) {
      // NW = 300, NORTH_WEST = NW;
      // NM = 301, NORTH_MID = NM;
      // NE = 302, NORTH_EAST = NE;
      // EM = 312, EAST_MID = EM;
      // SE = 322, SOUTH_EAST = SE;
      // SM = 321, SOUTH_MID = SM;
      // SW = 320, SOUTH_WEST = SW;
      // WM = 310, WEST_MID = WM;
      // MM = 311, MIDDLE = MM, M3 = MM;
      return r.getCell(pRow, pCol).getRect();
    }
    if (pTyp == 2) {
      // NH = 202, NORTH = NH;
      // EH = 221, EAST = EH;
      // SH = 212, SOUTH = SH;
      // WH = 220, WEST = WH;
      if (pRow > 1) {
        return r.getCol(pCol).getRect();
      } else if (pCol > 1) {
        return r.getRow(pRow).getRect();
      }
      return r.getCell(pRow, pCol).getRect();
    }
    if (pTyp == 4) {
      // MV = 441, MID_VERTICAL = MV;
      // MH = 414, MID_HORIZONTAL = MH;
      // M2 = 444, MIDDLE_BIG = M2;
      if (pRow > 3) {
        if (pCol > 3) {
          return r.getCell(1, 1).union(r.getCell(2, 2)).getRect();
        }
        return r.getCell(0, 1).union(r.getCell(3, 2)).getRect();
      } else if (pCol > 3) {
        return r.getCell(1, 0).union(r.getCell(2, 3)).getRect();
      }
      return r.getCell(pRow, pCol).getRect();
    }
    return rect;
  }

  /**
   * store info: this region is divided vertically into n even rows <br>
   * a preparation for using getRow()
   *
   * @param n number of rows
   * @return the top row
   */
  public Region setRows(int n) {
    return setRaster(n, 0);
  }

  /**
   * store info: this region is divided horizontally into n even columns <br>
   * a preparation for using getCol()
   *
   * @param n number of columns
   * @return the leftmost column
   */
  public Region setCols(int n) {
    return setRaster(0, n);
  }

  /**
   *
   * @return the number of rows or null
   */
  public int getRows() {
    return rows;
  }

  /**
   *
   * @return the row height or 0
   */
  public int getRowH() {
    return rowH;
  }

  /**
   *
   * @return the number of columns or 0
   */
  public int getCols() {
    return cols;
  }

  /**
   *
   * @return the columnwidth or 0
   */
  public int getColW() {
    return colW;
  }

  /**
   * Can be used to check, wether the Region currently has a valid raster
   *
   * @return true if it has a valid raster (either getCols or getRows or both would return &gt; 0) false otherwise
   */
  public boolean isRasterValid() {
    return (rows > 0 || cols > 0);
  }

  /**
   * store info: this region is divided into a raster of even cells <br>
   * a preparation for using getCell()<br>
   *
   * @param r number of rows
   * @param c number of columns
   * @return the topleft cell
   */
  public Region setRaster(int r, int c) {
    rows = Math.max(r, h);
    cols = Math.max(c, w);
    if (r > 0) {
      rowH = (int) (h / r);
      rowHd = h - r * rowH;
    }
    if (c > 0) {
      colW = (int) (w / c);
      colWd = w - c * colW;
    }
    return getCell(0, 0);
  }

  /**
   * get the specified row counting from 0, if rows or raster are setup negative counts reverse from the end (last = -1)
   * values outside range are 0 or last respectively
   *
   * @param r row number
   * @return the row as new region or the region itself, if no rows are setup
   */
  public Region getRow(int r) {
    if (rows == 0) {
      return this;
    }
    if (r < 0) {
      r = rows + r;
    }
    r = Math.max(0, r);
    r = Math.min(r, rows - 1);
    return Region.create(x, y + r * rowH, w, rowH);
  }

  public Region getRow(int r, int n) {
    return this;
  }

  /**
   * get the specified column counting from 0, if columns or raster are setup negative counts reverse from the end (last
   * = -1) values outside range are 0 or last respectively
   *
   * @param c column number
   * @return the column as new region or the region itself, if no columns are setup
   */
  public Region getCol(int c) {
    if (cols == 0) {
      return this;
    }
    if (c < 0) {
      c = cols + c;
    }
    c = Math.max(0, c);
    c = Math.min(c, cols - 1);
    return Region.create(x + c * colW, y, colW, h);
  }

  /**
   * divide the region in n columns and select column c as new Region
   *
   * @param c the column to select counting from 0 or negative to count from the end
   * @param n how many columns to devide in
   * @return the selected part or the region itself, if parameters are invalid
   */
  public Region getCol(int c, int n) {
    Region r = new Region(this);
    r.setCols(n);
    return r.getCol(c);
  }

  /**
   * get the specified cell counting from (0, 0), if a raster is setup <br>
   * negative counts reverse from the end (last = -1) values outside range are 0 or last respectively
   *
   * @param r row number
   * @param c column number
   * @return the cell as new region or the region itself, if no raster is setup
   */
  public Region getCell(int r, int c) {
    if (rows == 0) {
      return getCol(c);
    }
    if (cols == 0) {
      return getRow(r);
    }
    if (rows == 0 && cols == 0) {
      return this;
    }
    if (r < 0) {
      r = rows - r;
    }
    if (c < 0) {
      c = cols - c;
    }
    r = Math.max(0, r);
    r = Math.min(r, rows - 1);
    c = Math.max(0, c);
    c = Math.min(c, cols - 1);
    return Region.create(x + c * colW, y + r * rowH, colW, rowH);
  }
//</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="highlight">
  protected void updateSelf() {
    if (overlay != null) {
      highlight(false, null);
      highlight(true, null);
    }
  }

  protected Region silentHighlight(boolean onOff) {
    if (onOff && overlay == null) {
      return doHighlight(true, null, true);
    }
    if (!onOff && overlay != null) {
      return doHighlight(true, null, true);
    }
    return this;
  }

  /**
   * Toggle the regions Highlight visibility (red frame)
   *
   * @return the region itself
   */
  public Region highlight() {
    // Pass true if overlay is null, false otherwise
    highlight(overlay == null, null);
    return this;
  }

  /**
   * Toggle the regions Highlight visibility (frame of specified color)<br>
   * allowed color specifications for frame color: <br>
   * - a color name out of: black, blue, cyan, gray, green, magenta, orange, pink, red, white, yellow (lowercase and
   * uppercase can be mixed, internally transformed to all uppercase) <br>
   * - these colornames exactly written: lightGray, LIGHT_GRAY, darkGray and DARK_GRAY <br>
   * - a hex value like in HTML: #XXXXXX (max 6 hex digits) - an RGB specification as: #rrrgggbbb where rrr, ggg, bbb
   * are integer values in range 0 - 255 padded with leading zeros if needed (hence exactly 9 digits)
   *
   * @param color Color of frame
   * @return the region itself
   */
  public Region highlight(String color) {
    // Pass true if overlay is null, false otherwise
    highlight(overlay == null, color);
    return this;
  }

  /**
   * Sets the regions Highlighting border
   *
   * @param toEnable set overlay enabled or disabled
   * @param color Color of frame (see method highlight(color))
   */
  private Region highlight(boolean toEnable, String color) {
    return doHighlight(toEnable, color, false);
  }

  private Region doHighlight(boolean toEnable, String color, boolean silent) {
    if (isOtherScreen()) {
      return this;
    }
    if (!silent) {
      Debug.action("toggle highlight " + toString() + ": " + toEnable
          + (color != null ? " color: " + color : ""));
    }
    if (toEnable) {
      overlay = new ScreenHighlighter(getScreen(), color);
      overlay.highlight(this);
    } else {
      if (overlay != null) {
        overlay.close();
        overlay = null;
      }
    }
    return this;
  }

  /**
   * show the regions Highlight for the given time in seconds (red frame) if 0 - use the global Settings.SlowMotionDelay
   *
   * @param secs time in seconds
   * @return the region itself
   */
  public Region highlight(float secs) {
    return highlight(secs, null);
  }

  /**
   * show the regions Highlight for the given time in seconds (frame of specified color) if 0 - use the global
   * Settings.SlowMotionDelay
   *
   * @param secs time in seconds
   * @param color Color of frame (see method highlight(color))
   * @return the region itself
   */
  public Region highlight(float secs, String color) {
    if (isOtherScreen()) {
      return this;
    }
    if (secs < 0.1) {
      return highlight((int) secs, color);
    }
    Debug.action("highlight " + toString().replaceAll("%", "%%") + " for " + secs + " secs"
        + (color != null ? " color: " + color : ""));
    ScreenHighlighter _overlay = new ScreenHighlighter(getScreen(), color);
    _overlay.highlight(this, secs);
    return this;
  }

  /**
   * hack to implement the getLastMatch() convenience 0 means same as highlight() &lt; 0 same as highlight(secs) if
   * available the last match is highlighted
   *
   * @param secs seconds
   * @return this region
   */
  public Region highlight(int secs) {
    return highlight(secs, null);
  }

  /**
   * Show highlight in selected color
   *
   * @param secs time in seconds
   * @param color Color of frame (see method highlight(color))
   * @return this region
   */
  public Region highlight(int secs, String color) {
    if (isOtherScreen()) {
      return this;
    }
    if (secs > 0) {
      return highlight((float) secs, color);
    }
    if (lastMatch != null) {
      if (secs < 0) {
        return lastMatch.highlight((float) -secs, color);
      }
      return lastMatch.highlight(Settings.DefaultHighlightTime, color);
    }
    return this;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="find public methods">
  /**
   * Returns the image of the given Pattern, String or Image
   *
   * @param target The Pattern, String or Image
   * @return the Image
   */
  private <PSI> Image getImage(PSI target) {
    if (target instanceof Pattern) {
      //TODO new Pattern class
      return null; //((Pattern) target).getImage();
    } else if (target instanceof String) {
      return Image.get((String) target);
    } else if (target instanceof Image) {
      return (Image) target;
    } else {
      return null;
    }
  }

  /**
   * return false to skip <br>
   * return true to try again <br>
   * throw FindFailed to abort
   *
   * @param target Handles a failed find action
   * @throws FindFailed
   */
  private <PSI> boolean handleFindFailed(PSI target) throws FindFailed {
    return handleFindFailedShowDialog(target, false);
  }

  private <PSI> boolean handleFindFailedQuietly(PSI target) {
    try {
      return handleFindFailedShowDialog(target, false);
    } catch (FindFailed ex) {
    }
    return false;
  }

  private <PSI> boolean handleFindFailedImageMissing(PSI target) {
    boolean shouldHandle = false;
    try {
      shouldHandle = handleFindFailedShowDialog(target, true);
    } catch (FindFailed ex) {
      return false;
    }
    if (!shouldHandle) {
      return false;
    }
    getRobotForRegion().delay(500);
    ScreenImage img = getScreen().userCapture("capture missing image");
    if (img != null) {
      String path = ImagePath.getBundlePath();
      if (path == null) {
        return false;
      }
      String imgName = (String) target;
      img.getFile(path, imgName);
      return true;
    }
    return false;
  }

  private boolean handleFindFailedShowDialog(Object target, boolean shouldCapture) throws FindFailed {
    FindFailedResponse response;
    if (findFailedResponse == FindFailedResponse.PROMPT) {
      FindFailedDialog fd = new FindFailedDialog(target, shouldCapture);
      fd.setVisible(true);
      response = fd.getResponse();
      fd.dispose();
      wait(0.5);
    } else {
      response = findFailedResponse;
    }
    if (response == FindFailedResponse.SKIP) {
      return false;
    } else if (response == FindFailedResponse.RETRY) {
      return true;
    } else if (response == FindFailedResponse.ABORT) {
      String targetStr = target.toString();
      if (target instanceof String) {
        targetStr = targetStr.trim();
      }
      throw new FindFailed(String.format("%s not in %s", targetStr, this.toString()));
    }
    return false;
  }

  /**
   * WARNING: wait(long timeout) is taken by Java Object as final. This method catches any interruptedExceptions
   *
   * @param timeout The time to wait
   */
  public void wait(double timeout) {
    SX.pause(timeout);
  }

  public void wait(float timeout) {
    SX.pause(timeout);
  }

  /**
   * Waits for the Pattern, String or Image to appear until the AutoWaitTimeout value is exceeded.
   *
   * @param <PSI> Pattern, String or Image
   * @param target The target to search for
   * @return The found Match
   * @throws FindFailed if the Find operation finally failed
   */
  public <PSI> Match wait(PSI target) throws FindFailed {
    if (target instanceof Float || target instanceof Double) {
      wait(target);
      return new Match(this);
    }
    return wait(target, autoWaitTimeout);
  }

  /**
   * Waits for the Pattern, String or Image to appear or timeout (in second) is passed
   *
   * @param <PSI> Pattern, String or Image
   * @param target The target to search for
   * @param timeout Timeout in seconds
   * @return The found Match
   * @throws FindFailed if the Find operation finally failed
   */
  public <PSI> Match wait(PSI target, double timeout) throws FindFailed {
    Finder.Found found = doWait(target, timeout, FindType.ONE);
    return found.match;
  }

  private <PSI> Finder.Found doWait(PSI target, double timeout, FindType findType) throws FindFailed {
    boolean findAll = findType.equals(FindType.ALL);
    boolean findVanish = findType.equals(FindType.VANISH);
    boolean findOne = findType.equals(FindType.ONE);
    Finder.Found found = null;
    lastMatch = null;
    lastMatches = null;
    while (true) {
      try {
        found = doFind(null, target, timeout, findType);
        if (found == null) {
          return null;
        }
        if (found.success) {
          if (findOne || findVanish) {
            lastMatch = found.match;
          } else if (findAll) {
            lastMatches = found;
          } 
        }
      } catch (Exception ex) {
        if (ex instanceof IOException) {
          if (handleFindFailedImageMissing(target)) {
            continue;
          }
        }
        throw new FindFailed(ex.getMessage());
      }
      if ((findOne && lastMatch == null)
          || (findAll && lastMatches == null)
          || (findVanish && lastMatch != null)) {
        if (findVanish) {
          log(lvl, "%s(%d): did not vanish from %s", found.name, found.elapsed, lastMatch);
        } else {
          log(lvl, "%s(%d): did not appear", found.name, found.elapsed);
        }
      } else {
        if (lastMatch != null) {
          Image img = getImage(target);
          lastMatch.setImage(img);
          if (img != null) {
            img.setLastSeen(lastMatch.getRect(), lastMatch.getScore());
          }
        }
        if (findAll) {
          log(lvl, "%s(%d): at least one appeared", found.name, found.elapsed);
        } else if (findOne) {
          log(lvl, "%s(%d): at %s", found.name, found.elapsed, lastMatch);
        } else {
          if (lastMatch != null) {
            log(lvl, "%s(%d): vanished from %s", found.name, found.elapsed, lastMatch);
          } else {
            log(lvl, "%s(%d): not seen", found.name, found.elapsed);
          }
        }
        break;
      }
      if (!handleFindFailed(target)) {
        return found;
      }
    }
    log(lvl+1, found.toJSON());
    return found;
  }

  /**
   * finds the given Pattern, String or Image in the region and returns the best match.<b>
   * If AutoWaitTimeout is set, this is equivalent to wait().<b>
   * Otherwise only one search attempt will be done.
   *
   * @param <PSI> Pattern, String or Image
   * @param target A search criteria
   * @return If found, the element. null otherwise
   * @throws FindFailed if the Find operation failed
   */
  public <PSI> Match find(PSI target) throws FindFailed {
    return wait(target, autoWaitTimeout);
  }

  /**
   * Check if target exists (with the default autoWaitTimeout)
   *
   * @param <PSI> Pattern, String or Image
   * @param target Pattern, String or Image
   * @return the match (null if not found or image file missing)
   */
  public <PSI> Match exists(PSI target) {
    return exists(target, autoWaitTimeout);
  }

  /**
   * Check if target exists with a specified timeout<br>
   * timout = 0: returns immediately after first search
   *
   * @param <PSI> Pattern, String or Image
   * @param target The target to search for
   * @param timeout Timeout in seconds
   * @return the match (null if not found or image file missing)
   */
  public <PSI> Match exists(PSI target, double timeout) {
    Finder.Found found;
    try {
      found = doWait(target, timeout, FindType.ONE);
    } catch (FindFailed ex) {
      return null;
    }
    return found.match;
  }

  /**
   * finds all occurences of the given Pattern, String or Image in the region and returns an Iterator of Matches.
   *
   * @param <PSI> Pattern, String or Image
   * @param target to be searched for
   * @return All elements matching
   * @throws FindFailed if the Find operation fails or does not succeed within autoWaitTimeout
   */
  public <PSI> Iterator<Match> findAll(PSI target) throws FindFailed {
    return waitAll(target, autoWaitTimeout);
  }

  /**
   * Same as findAll, but waits for the given time until at least one target appears
   *
   * @param <PSI> Pattern, String or Image
   * @param target to be searched for
   * @param timeout time to wait in seconds
   * @return All elements matching
   * @throws FindFailed if the Find operation fails or does not succeed within timeout
   */
  public <PSI> Iterator<Match> waitAll(PSI target, double timeout) throws FindFailed {
    Finder.Found found = doWait(target, timeout, FindType.ALL);
    return found;
  }

  /**
   * finds all occurences of the given Pattern, String or Image in the region<br>
   * the returned matches are in the sequence top row to bottom row, left to right<br>
   * only works as expected for elements in a regular grid
   *
   * @param <PSI> Pattern, String or Image
   * @param target to be searched for
   * @return All elements matching as an array of matches
   */
  public <PSI> Match[] findAllByRow(PSI target) {
    Match[] matches = new Match[0];
    List<Match> mList = findAllCollect(target);
    if (mList.isEmpty()) {
      return null;
    }
    Collections.sort(mList, new Comparator<Match>() {
      @Override
      public int compare(Match m1, Match m2) {
        if (m1.y == m2.y) {
          return m1.x - m2.x;
        }
        return m1.y - m2.y;
      }
    });
    return mList.toArray(matches);
  }

  /**
   * finds all occurences of the given Pattern, String or Image in the region<br>
   * the returned matches are in the sequence column left to column right, top to bottom<br>
   * only works as expected for elements in a regular grid
   *
   * @param <PSI> Pattern, String or Image
   * @param target to be searched for
   * @return All elements matching as an array of matches
   */
  public <PSI> Match[] findAllByColumn(PSI target) {
    Match[] matches = new Match[0];
    List<Match> mList = findAllCollect(target);
    if (mList.isEmpty()) {
      return null;
    }
    Collections.sort(mList, new Comparator<Match>() {
      @Override
      public int compare(Match m1, Match m2) {
        if (m1.x == m2.x) {
          return m1.y - m2.y;
        }
        return m1.x - m2.x;
      }
    });
    return mList.toArray(matches);
  }

  private <PSI> List<Match> findAllCollect(PSI target) {
    Iterator<Match> mIter = null;
    try {
      mIter = findAll(target);
    } catch (Exception ex) {
      log(-1, "findAllCollect: %s", ex.getMessage());
      return null;
    }
    List<Match> mList = new ArrayList<Match>();
    while (mIter.hasNext()) {
      mList.add(mIter.next());
    }
    return mList;
  }


  public ScreenImage captureThis() {
    return new ScreenImage(this);
  }

  /**
   * check wether the region is exactly the given image, meaning same dimension and exact match
   *
   * @param fpImg image file name
   * @return the match or null if not equal
   */
  public boolean compare(String fpImg) {
    return compare(Image.get(fpImg));
  }

  /**
   * check wether the region is exactly the given image, meaning same dimension and exact match
   *
   * @param img Image object
   * @return the match or null if not equal
   */
  public boolean compare(Image img) {
    if (w == img.getWidth() && h == img.getHeight()) {
      return null != exists(new Pattern(img).exact(), 0);
    } else {
      return false;
    }
  }

  /**
   * Use findText() instead of find() in cases where the given string could be misinterpreted as an image filename
   *
   * @param text text
   * @param timeout time
   * @return the matched region containing the text
   * @throws org.sikuli.script.FindFailed if not found
   */
  public Match findText(String text, double timeout) throws FindFailed {
    // the leading/trailing tab is used to internally switch to text search directly
    return wait("\t" + text + "\t", timeout);
  }

  /**
   * Use findText() instead of find() in cases where the given string could be misinterpreted as an image filename
   *
   * @param text text
   * @return the matched region containing the text
   * @throws org.sikuli.script.FindFailed if not found
   */
  public Match findText(String text) throws FindFailed {
    return findText(text, autoWaitTimeout);
  }

  /**
   * Use findAllText() instead of findAll() in cases where the given string could be misinterpreted as an image filename
   *
   * @param text text
   * @return the matched region containing the text
   * @throws org.sikuli.script.FindFailed if not found
   */
  public Iterator<Match> findAllText(String text) throws FindFailed {
    // the leading/trailing tab is used to internally switch to text search directly
    return findAll("\t" + text + "\t");
  }

  /**
   * waits until target vanishes or timeout (in seconds) is passed (AutoWaitTimeout)
   *
   * @param <PSI> Pattern, String or Image
   * @param target The target to wait for it to vanish
   * @return true if the target vanishes, otherwise returns false.
   */
  public <PSI> boolean waitVanish(PSI target) {
    return waitVanish(target, autoWaitTimeout);
  }

  /**
   * waits until target vanishes or timeout (in seconds) is passed
   *
   * @param <PSI> Pattern, String or Image
   * @param target Pattern, String or Image
   * @param timeout time in seconds
   * @return true if target vanishes, false otherwise and if imagefile is missing.
   */
  public <PSI> boolean waitVanish(PSI target, double timeout) {
    Finder.Found found = null;
    try {
      found = doWait(target, timeout, FindType.VANISH);
    } catch (FindFailed ex) {
      return false;
    }
    return found != null && found.match == null;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="find internal methods">
  public enum FindType {

    ONE, ALL, VANISH, ANY, BEST
  }

  private <PSI> Finder.Found doFind(Image base, PSI ptn, double timeout, FindType findType) throws IOException {
    if (Debug.shouldHighlight()) {
      if (this.scr.getW() > w + 20 && this.scr.getH() > h + 20) {
        highlight(2, "#000255000");
      }
    }
    start();
    boolean success = false;
    boolean findAll = findType.equals(FindType.ALL);
    boolean findVanish = findType.equals(FindType.VANISH);
    Object[] result = new Object[]{null, null, null, null};
    Finder finder = null;
    boolean hasMatch = false;
    if (base == null) {
      finder = new Finder(this);
    } else {
      finder = new Finder(base);
    }
    Pattern pattern = finder.evalTarget(ptn);
    Finder.Found found = new Finder.Found(finder);
    found.pattern = pattern;
    
    int MaxTimePerScan = Math.max(0, (int) (1000.0 / waitScanRate));
    int timeoutMilli = Math.max(0, (int) (timeout * 1000));
    found.timeout = timeoutMilli;
    long begin_t = (new Date()).getTime();
    String begin_s = String.format("%d", begin_t);
    if (findAll) {
      found.name = "findall_" + begin_s;
      found.type = FindType.ALL;
    } else {
      found.name = (findVanish ? "vanish_" : "appear_") + begin_s;
    }
    log(lvl, "%s: %.1fs %s %s",
        found.name, timeout, pattern, this);

    Match matchVanish = null;
    if (findVanish) {
      finder.find(found);
      if (found.success) {
        matchVanish = found.match;
      } else {
        success = true;
      }
    }
    int loopCount = 0;
    if (!success) {
      do {
        loopCount++;
        long before_find = (new Date()).getTime();
        finder.find(found);
        if (found.success) {
          if (findVanish) {
            matchVanish = found.match;
            continue;
          }
          hasMatch = true;
          success = true;
        } else if (findVanish || timeoutMilli < MaxTimePerScan) {
          // should return after first search or if vanished
          success = true;
        }
        long findDuration = (new Date()).getTime() - before_find;
        log(lvl+1, "doFind: searchLoop: %d with %d msec", loopCount, findDuration);
        if (!success) {
          int rest = (int) (MaxTimePerScan - (findDuration - before_find));
          if (rest > 10) {
            getRobotForRegion().delay(rest);
          } else {
            getRobotForRegion().delay(10);
          }
        } else {
          break;
        }
      } while (begin_t + timeoutMilli > (new Date()).getTime());
    }
    log(lvl + 1, "doFind: %d searchloops", loopCount);
    if (hasMatch && findVanish) {
      found.match = matchVanish;
    }
    found.elapsed = end();
    return found;
  }

  //</editor-fold>
  
  //<editor-fold defaultstate="collapsed" desc="Observing">
  public Observer getObserver() {
    if (regionObserver == null) {
      regionObserver = new Observer(this);
    }
    return regionObserver;
  }

  /**
   * evaluate if at least one event observer is defined for this region (the observer need not be running)
   *
   * @return true, if the region has an observer with event observers
   */
  public boolean hasObservers() {
    if (regionObserver != null) {
      return regionObserver.hasObservers();
    }
    return false;
  }

  /**
   *
   * @return true if an observer is running for this region observing at least one event
   */
  public boolean isObserving() {
    if (regionObserver != null) {
      return regionObserver.isObserving();
    }
    return false;
  }

  /**
   *
   * @return true if any events have happened for this region, false otherwise
   */
  public boolean hasEvents() {
    if (regionObserver != null) {
      return regionObserver.hasEvents();
    }
    return false;
  }

  /**
   * the region's events are removed from the list
   *
   * @return the region's happened events as array if any (size might be 0)
   */
  public ObserveEvent[] getEvents() {
    if (regionObserver != null && regionObserver.hasEvents()) {
      return regionObserver.getEvents();
    }
    return new ObserveEvent[0];
  }

  /**
   * a subsequently started observer in this region should wait for target and notify the given observer about this
   * event<br>
   * for details about the observe event handler: {@link ObserverCallBack}<br>
   * for details about APPEAR/VANISH/CHANGE events: {@link ObserveEvent}<br>
   *
   * @param <PSI> Pattern, String or Image
   * @param target Pattern, String or Image
   * @param observer ObserverCallBack
   * @return the event's name
   */
  public <PSI> ObserveEvent onAppear(PSI target, Object observer) {
    return onEvent(target, observer, ObserveEvent.Type.APPEAR);
  }

  /**
   * a subsequently started observer in this region should wait for target success and details about the event can be
   * obtained using @{link Observing}<br>
   * for details about APPEAR/VANISH/CHANGE events: {@link ObserveEvent}<br>
   *
   * @param <PSI> Pattern, String or Image
   * @param target Pattern, String or Image
   * @return the event's name
   */
  public <PSI> ObserveEvent onAppear(PSI target) {
    return onEvent(target, null, ObserveEvent.Type.APPEAR);
  }

  private <PSIC> ObserveEvent onEvent(PSIC targetOrMinChanges, Object callback, ObserveEvent.Type obsType) {
    if (callback != null && (callback.getClass().getName().contains("org.python")
        || callback.getClass().getName().contains("org.jruby"))) {
      callback = new ObserverCallBack(callback, obsType);
    }
    ObserveEvent event = new ObserveEvent(this, obsType, targetOrMinChanges, (ObserverCallBack) callback);
    log(lvl, "%s: ON-%s %s: %s", toString(), obsType,
        (callback == null ? "" : " with callback"), targetOrMinChanges);
    return event;
  }

  /**
   * a subsequently started observer in this region should wait for the target to vanish and notify the given observer
   * about this event<br>
   * for details about the observe event handler: {@link ObserverCallBack}<br>
   * for details about APPEAR/VANISH/CHANGE events: {@link ObserveEvent}<br>
   *
   * @param <PSI> Pattern, String or Image
   * @param target Pattern, String or Image
   * @param observer ObserverCallBack
   * @return the event's name
   */
  public <PSI> ObserveEvent onVanish(PSI target, Object observer) {
    return onEvent(target, observer, ObserveEvent.Type.VANISH);
  }

  /**
   * a subsequently started observer in this region should wait for the target to vanish success and details about the
   * event can be obtained using @{link Observing}<br>
   * for details about APPEAR/VANISH/CHANGE events: {@link ObserveEvent}<br>
   *
   * @param <PSI> Pattern, String or Image
   * @param target Pattern, String or Image
   * @return the event's name
   */
  public <PSI> ObserveEvent onVanish(PSI target) {
    return onEvent(target, null, ObserveEvent.Type.VANISH);
  }

  /**
   * a subsequently started observer in this region should wait for changes in the region and notify the given observer
   * about this event for details about the observe event handler: {@link ObserverCallBack} for details about
   * APPEAR/VANISH/CHANGE events: {@link ObserveEvent}
   *
   * @param threshold minimum size of changes (rectangle threshhold x threshold)
   * @param observer ObserverCallBack
   * @return the event's name
   */
  public ObserveEvent onChange(int threshold, Object observer) {
    return onEvent((threshold > 0 ? threshold : Settings.ObserveMinChangedPixels),
        observer, ObserveEvent.Type.CHANGE);
  }

  /**
   * a subsequently started observer in this region should wait for changes in the region success and details about the
   * event can be obtained using @{link Observing}<br>
   * for details about APPEAR/VANISH/CHANGE events: {@link ObserveEvent}
   *
   * @param threshold minimum size of changes (rectangle threshhold x threshold)
   * @return the event's name
   */
  public ObserveEvent onChange(int threshold) {
    return onEvent((threshold > 0 ? threshold : Settings.ObserveMinChangedPixels),
        null, ObserveEvent.Type.CHANGE);
  }

  /**
   * a subsequently started observer in this region should wait for changes in the region and notify the given observer
   * about this event <br>
   * minimum size of changes used: Settings.ObserveMinChangedPixels for details about the observe event handler:
   * {@link ObserverCallBack} for details about APPEAR/VANISH/CHANGE events: {@link ObserveEvent}
   *
   * @param observer ObserverCallBack
   * @return the event's name
   */
  public ObserveEvent onChange(Object observer) {
    return onEvent(Settings.ObserveMinChangedPixels, observer, ObserveEvent.Type.CHANGE);
  }

  /**
   * a subsequently started observer in this region should wait for changes in the region success and details about the
   * event can be obtained using @{link Observing}<br>
   * minimum size of changes used: Settings.ObserveMinChangedPixels for details about APPEAR/VANISH/CHANGE events:
   * {@link ObserveEvent}
   *
   * @return the event's name
   */
  public ObserveEvent onChange() {
    return onEvent(Settings.ObserveMinChangedPixels, null, ObserveEvent.Type.CHANGE);
  }


  /**
   * start an observer in this region that runs forever (use stopObserving() in handler) for details about the observe
   * event handler: {@link ObserverCallBack} for details about APPEAR/VANISH/CHANGE events: {@link ObserveEvent}
   *
   * @return false if not possible, true if events have happened
   */
  public boolean observe() {
    return observe(Float.POSITIVE_INFINITY);
  }

  /**
   * start an observer in this region for the given time for details about the observe event handler:
   * {@link ObserverCallBack} for details about APPEAR/VANISH/CHANGE events: {@link ObserveEvent}
   *
   * @param secs time in seconds the observer should run
   * @return false if not possible, true if events have happened
   */
  public boolean observe(double secs) {
    return observeDo(secs);
  }

  /**
   * INTERNAL USE ONLY: for use with scripting API bridges
   *
   * @param secs time in seconds the observer should run
   * @param bg run in background true/false
   * @return false if not possible, true if events have happened
   */
  public boolean observeJ(double secs, boolean bg) {
    if (bg) {
      return observeInBackground(secs);
    } else {
      return observeDo(secs);
    }
  }

  private boolean observeDo(double secs) {
    if (regionObserver == null) {
      log(-1, "observe: Nothing to observe (Region might be invalid): %s", this);
      return false;
    }
    if (observing) {
      log(-1, "observe: already running for this region. Only one allowed!");
      return false;
    }
    log(lvl, "observe: starting in %s (%.1f secs)", this, secs);
    int MaxTimePerScan = (int) (1000.0 / observeScanRate);
    long begin_t = (new Date()).getTime();
    long stop_t;
    if (secs > Long.MAX_VALUE) {
      stop_t = Long.MAX_VALUE;
    } else {
      stop_t = begin_t + (long) (secs * 1000);
    }
    
    // prepare observing in this region
    observing = regionObserver.init();
    if (!observing) {
      log(-1, "observe: init not possible for %s", this);
      return false;
    }

    // run observing for this region for the given time
    while (observing && stop_t > (new Date()).getTime()) {
      long before_find = (new Date()).getTime();
      observing = regionObserver.run();
      if (observing) {
        long after_find = (new Date()).getTime();
        try {
          if (after_find - before_find < MaxTimePerScan) {
            Thread.sleep((int) (MaxTimePerScan - (after_find - before_find)));
          }
        } catch (Exception e) {
        }
      }
    }
    
    // observing ended
    if (observing) {
      observing = false;
      log(lvl, "observe: stopped due to timeout in %s (%.1f secs)", this, secs );
    } else {
      log(lvl, "observe: ended for: %s", this);
    }
    return true;
  }

  /**
   * start an observer in this region for the given time that runs in background for details about the observe event
   * handler: {@link ObserverCallBack} for details about APPEAR/VANISH/CHANGE events: {@link ObserveEvent}
   *
   * @param secs time in seconds the observer should run
   * @return false if not possible, true otherwise
   */
  public boolean observeInBackground(double secs) {
    if (observing) {
      Debug.error("Region: observeInBackground: already running for this region. Only one allowed!");
      return false;
    }
    log(lvl, "entering observeInBackground for %f secs", secs);
    Thread observeThread = new Thread(new ObserveThread(secs));
    observeThread.start();
    log(lvl, "observeInBackground now running");
    return true;
  }
  
    public boolean observeInBackground() {
      return observeInBackground(Float.POSITIVE_INFINITY);
    }

  private class ObserveThread implements Runnable {

    private double time;

    ObserveThread(double time) {
      this.time = time;
    }

    @Override
    public void run() {
      observe(time);
    }
  }

  /**
   * stops a running observer
   */
  public void stopObserver() {
    log(lvl, "observe: request to stop observer for " + this.toString());
    observing = false;
    regionObserver.stop();
  }

  /**
   * stops a running observer printing an info message
   *
   * @param message text
   */
  public void stopObserver(String message) {
    Debug.info(message);
    stopObserver();
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Mouse actions - clicking">
  public Location checkMatch() {
    if (lastMatch != null) {
      return lastMatch.getTarget();
    }
    return getTarget();
  }

  private <PSIMRL> Location getLocationFromTarget(PSIMRL target) throws FindFailed {
    if (target instanceof Pattern || target instanceof String || target instanceof Image) {
      Match m = find(target);
      if (m != null) {
        if (isOtherScreen()) {
          return m.getTarget().setOtherScreen(scr);
        } else {
          return m.getTarget();
        }
      }
      return null;
    }
    if (target instanceof Match) {
      return ((Match) target).getTarget();
    }
    if (target instanceof Region) {
      return ((Region) target).getCenter();
    }
    if (target instanceof Location) {
      return new Location((Location) target);
    }
    return null;
  }

  /**
   * move the mouse pointer to region's last successful match <br>use center if no lastMatch <br>
   * if region is a match: move to targetOffset <br>same as mouseMove
   *
   * @return 1 if possible, 0 otherwise
   */
  public int hover() {
    try { // needed to cut throw chain for FindFailed
      return hover(checkMatch());
    } catch (FindFailed ex) {
    }
    return 0;
  }

  /**
   * move the mouse pointer to the given target location<br> same as mouseMove<br> Pattern or Filename - do a find
   * before and use the match<br> Region - position at center<br> Match - position at match's targetOffset<br> Location
   * - position at that point<br>
   *
   * @param <PFRML> to search: Pattern, Filename, Text, Region, Match or Location
   * @param target Pattern, Filename, Text, Region, Match or Location
   * @return 1 if possible, 0 otherwise
   * @throws FindFailed for Pattern or Filename
   */
  public <PFRML> int hover(PFRML target) throws FindFailed {
    log(lvl, "hover: " + target);
    return mouseMove(target);
  }

  /**
   * left click at the region's last successful match <br>use center if no lastMatch <br>if region is a match: click
   * targetOffset
   *
   * @return 1 if possible, 0 otherwise
   */
  public int click() {
    try { // needed to cut throw chain for FindFailed
      return click(checkMatch(), 0);
    } catch (FindFailed ex) {
      return 0;
    }
  }

  /**
   * left click at the given target location<br> Pattern or Filename - do a find before and use the match<br> Region -
   * position at center<br> Match - position at match's targetOffset<br>
   * Location - position at that point<br>
   *
   * @param <PFRML> to search: Pattern, Filename, Text, Region, Match or Location
   * @param target Pattern, Filename, Text, Region, Match or Location
   * @return 1 if possible, 0 otherwise
   * @throws FindFailed for Pattern or Filename
   */
  public <PFRML> int click(PFRML target) throws FindFailed {
    return click(target, 0);
  }

  /**
   * left click at the given target location<br> holding down the given modifier keys<br>
   * Pattern or Filename - do a find before and use the match<br> Region - position at center<br>
   * Match - position at match's targetOffset<br> Location - position at that point<br>
   *
   * @param <PFRML> to search: Pattern, Filename, Text, Region, Match or Location
   * @param target Pattern, Filename, Text, Region, Match or Location
   * @param modifiers the value of the resulting bitmask (see KeyModifier)
   * @return 1 if possible, 0 otherwise
   * @throws FindFailed for Pattern or Filename
   */
  public <PFRML> int click(PFRML target, Integer modifiers) throws FindFailed {
    Location loc = getLocationFromTarget(target);
    int ret = Mouse.click(loc, InputEvent.BUTTON1_MASK, modifiers, false, this);

    //TODO      SikuliActionManager.getInstance().clickTarget(this, target, _lastScreenImage, _lastMatch);
    return ret;
  }

  /**
   * double click at the region's last successful match <br>use center if no lastMatch <br>if region is a match: click
   * targetOffset
   *
   * @return 1 if possible, 0 otherwise
   */
  public int doubleClick() {
    try { // needed to cut throw chain for FindFailed
      return doubleClick(checkMatch(), 0);
    } catch (FindFailed ex) {
      return 0;
    }
  }

  /**
   * double click at the given target location<br> Pattern or Filename - do a find before and use the match<br> Region -
   * position at center<br> Match - position at match's targetOffset<br>
   * Location - position at that point<br>
   *
   * @param <PFRML> Pattern, Filename, Text, Region, Match or Location
   * @param target Pattern, Filename, Text, Region, Match or Location
   * @return 1 if possible, 0 otherwise
   * @throws FindFailed for Pattern or Filename
   */
  public <PFRML> int doubleClick(PFRML target) throws FindFailed {
    return doubleClick(target, 0);
  }

  /**
   * double click at the given target location<br> holding down the given modifier keys<br>
   * Pattern or Filename - do a find before and use the match<br> Region - position at center<br > Match - position at
   * match's targetOffset<br> Location - position at that point<br>
   *
   * @param <PFRML> Pattern, Filename, Text, Region, Match or Location
   * @param target Pattern, Filename, Text, Region, Match or Location
   * @param modifiers the value of the resulting bitmask (see KeyModifier)
   * @return 1 if possible, 0 otherwise
   * @throws FindFailed for Pattern or Filename
   */
  public <PFRML> int doubleClick(PFRML target, Integer modifiers) throws FindFailed {
    Location loc = getLocationFromTarget(target);
    int ret = Mouse.click(loc, InputEvent.BUTTON1_MASK, modifiers, true, this);

    //TODO      SikuliActionManager.getInstance().doubleClickTarget(this, target, _lastScreenImage, _lastMatch);
    return ret;
  }

  /**
   * right click at the region's last successful match <br>use center if no lastMatch <br>if region is a match: click
   * targetOffset
   *
   * @return 1 if possible, 0 otherwise
   */
  public int rightClick() {
    try { // needed to cut throw chain for FindFailed
      return rightClick(checkMatch(), 0);
    } catch (FindFailed ex) {
      return 0;
    }
  }

  /**
   * right click at the given target location<br> Pattern or Filename - do a find before and use the match<br> Region -
   * position at center<br> Match - position at match's targetOffset<br > Location - position at that point<br>
   *
   * @param <PFRML> Pattern, Filename, Text, Region, Match or Location
   * @param target Pattern, Filename, Text, Region, Match or Location
   * @return 1 if possible, 0 otherwise
   * @throws FindFailed for Pattern or Filename
   */
  public <PFRML> int rightClick(PFRML target) throws FindFailed {
    return rightClick(target, 0);
  }

  /**
   * right click at the given target location<br> holding down the given modifier keys<br>
   * Pattern or Filename - do a find before and use the match<br> Region - position at center<br > Match - position at
   * match's targetOffset<br> Location - position at that point<br>
   *
   * @param <PFRML> Pattern, Filename, Text, Region, Match or Location
   * @param target Pattern, Filename, Text, Region, Match or Location
   * @param modifiers the value of the resulting bitmask (see KeyModifier)
   * @return 1 if possible, 0 otherwise
   * @throws FindFailed for Pattern or Filename
   */
  public <PFRML> int rightClick(PFRML target, Integer modifiers) throws FindFailed {
    Location loc = getLocationFromTarget(target);
    int ret = Mouse.click(loc, InputEvent.BUTTON3_MASK, modifiers, false, this);

    //TODO      SikuliActionManager.getInstance().rightClickTarget(this, target, _lastScreenImage, _lastMatch);
    return ret;
  }

  /**
   * time in milliseconds to delay between button down/up at next click only (max 1000)
   *
   * @param millisecs value
   */
  public void delayClick(int millisecs) {
    Settings.ClickDelay = millisecs;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Mouse actions - drag & drop">
  /**
   * Drag from region's last match and drop at given target <br>applying Settings.DelayAfterDrag and DelayBeforeDrop
   * <br> using left mouse button
   *
   * @param <PFRML> Pattern, Filename, Text, Region, Match or Location
   * @param target Pattern, Filename, Text, Region, Match or Location
   * @return 1 if possible, 0 otherwise
   * @throws FindFailed if the Find operation failed
   */
  public <PFRML> int dragDrop(PFRML target) throws FindFailed {
    return dragDrop(lastMatch, target);
  }

  /**
   * Drag from a position and drop to another using left mouse button<br>applying Settings.DelayAfterDrag and
   * DelayBeforeDrop
   *
   * @param <PFRML> Pattern, Filename, Text, Region, Match or Location
   * @param t1 source position
   * @param t2 destination position
   * @return 1 if possible, 0 otherwise
   * @throws FindFailed if the Find operation failed
   */
  public <PFRML> int dragDrop(PFRML t1, PFRML t2) throws FindFailed {
    Location loc1 = getLocationFromTarget(t1);
    Location loc2 = getLocationFromTarget(t2);
    int retVal = 0;
    if (loc1 != null && loc2 != null) {
      IRobot r1 = loc1.getGlobalRobot();
      IRobot r2 = loc2.getGlobalRobot();
      if (r1 != null && r2 != null) {
        Mouse.use(this);
        r1.smoothMove(loc1);
        r1.delay((int) (Settings.DelayBeforeMouseDown * 1000));
        r1.mouseDown(InputEvent.BUTTON1_MASK);
        double DelayBeforeDrag = Settings.DelayBeforeDrag;
        r1.delay((int) (DelayBeforeDrag * 1000));
        r2.smoothMove(loc2);
        r2.delay((int) (Settings.DelayBeforeDrop * 1000));
        r2.mouseUp(InputEvent.BUTTON1_MASK);
        Mouse.let(this);
        retVal = 1;
      }
    }
    Settings.DelayBeforeMouseDown = Settings.DelayValue;
    Settings.DelayBeforeDrag = -Settings.DelayValue;
    Settings.DelayBeforeDrop = Settings.DelayValue;
    return retVal;
  }

  /**
   * Prepare a drag action: move mouse to given target <br>press and hold left mouse button <br >wait
   * Settings.DelayAfterDrag
   *
   * @param <PFRML> Pattern, Filename, Text, Region, Match or Location
   * @param target Pattern, Filename, Text, Region, Match or Location
   * @return 1 if possible, 0 otherwise
   * @throws FindFailed if not found
   */
  public <PFRML> int drag(PFRML target) throws FindFailed {
    Location loc = getLocationFromTarget(target);
    int retVal = 0;
    if (loc != null) {
      IRobot r = loc.getGlobalRobot();
      if (r != null) {
        Mouse.use(this);
        r.smoothMove(loc);
        r.delay((int) (Settings.DelayBeforeMouseDown * 1000));
        r.mouseDown(InputEvent.BUTTON1_MASK);
        double DelayBeforeDrag = Settings.DelayBeforeDrag;
        r.delay((int) (DelayBeforeDrag * 1000));
        r.waitForIdle();
        Mouse.let(this);
        retVal = 1;
      }
    }
    Settings.DelayBeforeMouseDown = Settings.DelayValue;
    Settings.DelayBeforeDrag = -Settings.DelayValue;
    Settings.DelayBeforeDrop = Settings.DelayValue;
    return retVal;
  }

  /**
   * finalize a drag action with a drop: move mouse to given target <br>
   * wait Settings.DelayBeforeDrop <br>
   * before releasing the left mouse button
   *
   * @param <PFRML> Pattern, Filename, Text, Region, Match or Location
   * @param target Pattern, Filename, Text, Region, Match or Location
   * @return 1 if possible, 0 otherwise
   * @throws FindFailed if not found
   */
  public <PFRML> int dropAt(PFRML target) throws FindFailed {
    Location loc = getLocationFromTarget(target);
    int retVal = 0;
    if (loc != null) {
      IRobot r = loc.getGlobalRobot();
      if (r != null) {
        Mouse.use(this);
        r.smoothMove(loc);
        r.delay((int) (Settings.DelayBeforeDrop * 1000));
        r.mouseUp(InputEvent.BUTTON1_MASK);
        r.waitForIdle();
        Mouse.let(this);
        retVal = 1;
      }
    }
    Settings.DelayBeforeMouseDown = Settings.DelayValue;
    Settings.DelayBeforeDrag = -Settings.DelayValue;
    Settings.DelayBeforeDrop = Settings.DelayValue;
    return retVal;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Mouse actions - low level + Wheel">
  /**
   * press and hold the specified buttons - use + to combine Button.LEFT left mouse button Button.MIDDLE middle mouse
   * button Button.RIGHT right mouse button
   *
   * @param buttons spec
   */
  public void mouseDown(int buttons) {
    Mouse.down(buttons, this);
  }

  /**
   * release all currently held buttons
   */
  public void mouseUp() {
    Mouse.up(0, this);
  }

  /**
   * release the specified mouse buttons (see mouseDown) if buttons==0, all currently held buttons are released
   *
   * @param buttons spec
   */
  public void mouseUp(int buttons) {
    Mouse.up(buttons, this);
  }

  /**
   * move the mouse pointer to the region's last successful match<br>same as hover<br>
   *
   * @return 1 if possible, 0 otherwise
   */
  public int mouseMove() {
    if (lastMatch != null) {
      try {
        return mouseMove(lastMatch);
      } catch (FindFailed ex) {
        return 0;
      }
    }
    return 0;
  }

  /**
   * move the mouse pointer to the given target location<br> same as hover<br> Pattern or Filename - do a find before
   * and use the match<br> Region - position at center<br> Match - position at match's targetOffset<br>
   * Location - position at that point<br>
   *
   * @param <PFRML> Pattern, Filename, Text, Region, Match or Location
   * @param target Pattern, Filename, Text, Region, Match or Location
   * @return 1 if possible, 0 otherwise
   * @throws FindFailed for Pattern or Filename
   */
  public <PFRML> int mouseMove(PFRML target) throws FindFailed {
    Location loc = getLocationFromTarget(target);
    return Mouse.move(loc, this);
  }

  /**
   * move the mouse from the current position to the offset position given by the parameters
   *
   * @param xoff horizontal offset (&lt; 0 left, &gt; 0 right)
   * @param yoff vertical offset (&lt; 0 up, &gt; 0 down)
   * @return 1 if possible, 0 otherwise
   */
  public int mouseMove(int xoff, int yoff) {
    try {
      return mouseMove(Mouse.at().offset(xoff, yoff));
    } catch (Exception ex) {
      return 0;
    }
  }

  /**
   * Move the wheel at the current mouse position<br> the given steps in the given direction: <br >Button.WHEEL_DOWN,
   * Button.WHEEL_UP
   *
   * @param direction to move the wheel
   * @param steps the number of steps
   * @return 1 in any case
   */
  public int wheel(int direction, int steps) {
    Mouse.wheel(direction, steps, this);
    return 1;
  }

  /**
   * move the mouse pointer to the given target location<br> and move the wheel the given steps in the given direction:
   * <br>Button.WHEEL_DOWN, Button.WHEEL_UP
   *
   * @param <PFRML> Pattern, Filename, Text, Region, Match or Location target
   * @param target Pattern, Filename, Text, Region, Match or Location
   * @param direction to move the wheel
   * @param steps the number of steps
   * @return 1 if possible, 0 otherwise
   * @throws FindFailed if the Find operation failed
   */
  public <PFRML> int wheel(PFRML target, int direction, int steps) throws FindFailed {
    return wheel(target, direction, steps, Mouse.WHEEL_STEP_DELAY);
  }

  /**
   * move the mouse pointer to the given target location<br> and move the wheel the given steps in the given direction:
   * <br>Button.WHEEL_DOWN, Button.WHEEL_UP
   *
   * @param <PFRML> Pattern, Filename, Text, Region, Match or Location target
   * @param target Pattern, Filename, Text, Region, Match or Location
   * @param direction to move the wheel
   * @param steps the number of steps
   * @param stepDelay number of miliseconds to wait when incrementing the step value
   * @return 1 if possible, 0 otherwise
   * @throws FindFailed if the Find operation failed
   */
  public <PFRML> int wheel(PFRML target, int direction, int steps, int stepDelay) throws FindFailed {
    Location loc = getLocationFromTarget(target);
    if (loc != null) {
      Mouse.use(this);
      Mouse.keep(this);
      Mouse.move(loc, this);
      Mouse.wheel(direction, steps, this, stepDelay);
      Mouse.let(this);
      return 1;
    }
    return 0;
  }

  /**
   *
   * @return current location of mouse pointer
   * @deprecated use {@link Mouse#at()} instead
   */
  @Deprecated
  public static Location atMouse() {
    return Mouse.at();
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Keyboard actions + paste">
  /**
   * press and hold the given key use a constant from java.awt.event.KeyEvent which might be special in the current
   * machine/system environment
   *
   * @param keycode Java KeyCode
   */
  public void keyDown(int keycode) {
    getRobotForRegion().keyDown(keycode);
  }

  /**
   * press and hold the given keys including modifier keys <br>use the key constants defined in class Key, <br>which
   * only provides a subset of a US-QWERTY PC keyboard layout <br>might be mixed with simple characters
   * <br>use + to concatenate Key constants
   *
   * @param keys valid keys
   */
  public void keyDown(String keys) {
    getRobotForRegion().keyDown(keys);
  }

  /**
   * release all currently pressed keys
   */
  public void keyUp() {
    getRobotForRegion().keyUp();
  }

  /**
   * release the given keys (see keyDown(keycode) )
   *
   * @param keycode Java KeyCode
   */
  public void keyUp(int keycode) {
    getRobotForRegion().keyUp(keycode);
  }

  /**
   * release the given keys (see keyDown(keys) )
   *
   * @param keys valid keys
   */
  public void keyUp(String keys) {
    getRobotForRegion().keyUp(keys);
  }

  /**
   * Compact alternative for type() with more options <br>
   * - special keys and options are coded as #XN. or #X+ or #X- <br>
   * where X is a refrence for a special key and N is an optional repeat factor <br>
   * A modifier key as #X. modifies the next following key<br>
   * the trailing . ends the special key, the + (press and hold) or - (release) does the same, <br>
   * but signals press-and-hold or release additionally.<br>
   * except #W / #w all special keys are not case-sensitive<br>
   * a #wn. inserts a wait of n millisecs or n secs if n less than 60 <br>
   * a #Wn. sets the type delay for the following keys (must be &gt; 60 and denotes millisecs) - otherwise taken as
   * normal wait<br>
   * Example: wait 2 secs then type CMD/CTRL - N then wait 1 sec then type DOWN 3 times<br>
   * Windows/Linux: write("#w2.#C.n#W1.#d3.")<br>
   * Mac: write("#w2.#M.n#W1.#D3.")<br>
   * for more details about the special key codes and examples consult the docs <br>
   *
   * @param text a coded text interpreted as a series of key actions (press/hold/release)
   * @return 0 for success 1 otherwise
   */
  public int write(String text) {
    Debug.info("Write: " + text);
    char c;
    String token, tokenSave;
    String modifier = "";
    int k;
    IRobot robot = getRobotForRegion();
    int pause = 20 + (Settings.TypeDelay > 1 ? 1000 : (int) (Settings.TypeDelay * 1000));
    Settings.TypeDelay = 0.0;
    robot.typeStarts();
    for (int i = 0; i < text.length(); i++) {
      log(lvl + 1, "write: (%d) %s", i, text.substring(i));
      c = text.charAt(i);
      token = null;
      boolean isModifier = false;
      if (c == '#') {
        if (text.charAt(i + 1) == '#') {
          log(3, "write at: %d: %s", i, c);
          i += 1;
          continue;
        }
        if (text.charAt(i + 2) == '+' || text.charAt(i + 2) == '-') {
          token = text.substring(i, i + 3);
          isModifier = true;
        } else if (-1 < (k = text.indexOf('.', i))) {
          if (k > -1) {
            token = text.substring(i, k + 1);
            if (token.length() > Key.keyMaxLength || token.substring(1).contains("#")) {
              token = null;
            }
          }
        }
      }
      Integer key = -1;
      if (token == null) {
        log(lvl + 1, "write: %d: %s", i, c);
      } else {
        log(lvl + 1, "write: token at %d: %s", i, token);
        int repeat = 0;
        if (token.toUpperCase().startsWith("#W")) {
          if (token.length() > 3) {
            i += token.length() - 1;
            int t = 0;
            try {
              t = Integer.parseInt(token.substring(2, token.length() - 1));
            } catch (NumberFormatException ex) {
            }
            if ((token.startsWith("#w") && t > 60)) {
              pause = 20 + (t > 1000 ? 1000 : t);
              log(lvl + 1, "write: type delay: " + t);
            } else {
              log(lvl + 1, "write: wait: " + t);
              robot.delay((t < 60 ? t * 1000 : t));
            }
            continue;
          }
        }
        tokenSave = token;
        token = token.substring(0, 2).toUpperCase() + ".";
        if (Key.isRepeatable(token)) {
          try {
            repeat = Integer.parseInt(tokenSave.substring(2, tokenSave.length() - 1));
          } catch (NumberFormatException ex) {
            token = tokenSave;
          }
        } else if (tokenSave.length() == 3 && Key.isModifier(tokenSave.toUpperCase())) {
          i += tokenSave.length() - 1;
          modifier += tokenSave.substring(1, 2).toUpperCase();
          continue;
        } else {
          token = tokenSave;
        }
        if (-1 < (key = Key.toJavaKeyCodeFromText(token))) {
          if (repeat > 0) {
            log(lvl + 1, "write: %s Repeating: %d", token, repeat);
          } else {
            log(lvl + 1, "write: %s", tokenSave);
            repeat = 1;
          }
          i += tokenSave.length() - 1;
          if (isModifier) {
            if (tokenSave.endsWith("+")) {
              robot.keyDown(key);
            } else {
              robot.keyUp(key);
            }
            continue;
          }
          if (repeat > 1) {
            for (int n = 0; n < repeat; n++) {
              robot.typeKey(key.intValue());
            }
            continue;
          }
        }
      }
      if (!modifier.isEmpty()) {
        log(lvl + 1, "write: modifier + " + modifier);
        for (int n = 0; n < modifier.length(); n++) {
          robot.keyDown(Key.toJavaKeyCodeFromText(String.format("#%s.", modifier.substring(n, n + 1))));
        }
      }
      if (key > -1) {
        robot.typeKey(key.intValue());
      } else {
        robot.typeChar(c, IRobot.KeyMode.PRESS_RELEASE);
      }
      if (!modifier.isEmpty()) {
        log(lvl + 1, "write: modifier - " + modifier);
        for (int n = 0; n < modifier.length(); n++) {
          robot.keyUp(Key.toJavaKeyCodeFromText(String.format("#%s.", modifier.substring(n, n + 1))));
        }
      }
      robot.delay(pause);
      modifier = "";
    }

    robot.typeEnds();
    robot.waitForIdle();
    return 0;
  }

  /**
   * enters the given text one character/key after another using keyDown/keyUp
   * <br>about the usable Key constants see keyDown(keys) <br>Class Key only provides a subset of a US-QWERTY PC
   * keyboard layout<br>the text is entered at the current position of the focus/carret
   *
   * @param text containing characters and/or Key constants
   * @return 1 if possible, 0 otherwise
   */
  public int type(String text) {
    try {
      return keyin(null, text, 0);
    } catch (FindFailed ex) {
      return 0;
    }
  }

  /**
   * enters the given text one character/key after another using keyDown/keyUp<br>while holding down the given modifier
   * keys <br>about the usable Key constants see keyDown(keys) <br>Class Key only provides a subset of a US-QWERTY PC
   * keyboard layout<br>the text is entered at the current position of the focus/carret
   *
   * @param text containing characters and/or Key constants
   * @param modifiers constants according to class KeyModifiers
   * @return 1 if possible, 0 otherwise
   */
  public int type(String text, int modifiers) {
    try {
      return keyin(null, text, modifiers);
    } catch (FindFailed findFailed) {
      return 0;
    }
  }

  /**
   * enters the given text one character/key after another using
   *
   * keyDown/keyUp<br>while holding down the given modifier keys <br>about the usable Key constants see keyDown(keys)
   * <br>Class Key only provides a subset of a US-QWERTY PC keyboard layout<br>the text is entered at the current
   * position of the focus/carret
   *
   *
   * @param text containing characters and/or Key constants
   * @param modifiers constants according to class Key - combine using +
   * @return 1 if possible, 0 otherwise
   */
  public int type(String text, String modifiers) {
    String target = null;
    int modifiersNew = Key.convertModifiers(modifiers);
    if (modifiersNew == 0) {
      target = text;
      text = modifiers;
    }
    try {
      return keyin(target, text, modifiersNew);
    } catch (FindFailed findFailed) {
      return 0;
    }
  }

  /**
   * first does a click(target) at the given target position to gain focus/carret <br>enters the given text one
   * character/key after another using keyDown/keyUp <br>about the usable Key constants see keyDown(keys)
   * <br>Class Key only provides a subset of a US-QWERTY PC keyboard layout
   *
   * @param <PFRML> Pattern, Filename, Text, Region, Match or Location
   * @param target Pattern, Filename, Text, Region, Match or Location
   * @param text containing characters and/or Key constants
   * @return 1 if possible, 0 otherwise
   * @throws FindFailed if not found
   */
  public <PFRML> int type(PFRML target, String text) throws FindFailed {
    return keyin(target, text, 0);
  }

  /**
   * first does a click(target) at the given target position to gain focus/carret <br>enters the given text one
   * character/key after another using keyDown/keyUp <br>while holding down the given modifier keys<br>about the usable
   * Key constants see keyDown(keys) <br>Class Key only provides a subset of a US-QWERTY PC keyboard layout
   *
   * @param <PFRML> Pattern, Filename, Text, Region, Match or Location
   * @param target Pattern, Filename, Text, Region, Match or Location
   * @param text containing characters and/or Key constants
   * @param modifiers constants according to class KeyModifiers
   * @return 1 if possible, 0 otherwise
   * @throws FindFailed if not found
   */
  public <PFRML> int type(PFRML target, String text, int modifiers) throws FindFailed {
    return keyin(target, text, modifiers);
  }

  /**
   * first does a click(target) at the given target position to gain focus/carret <br>enters the given text one
   * character/key after another using keyDown/keyUp <br>while holding down the given modifier keys<br>about the usable
   * Key constants see keyDown(keys) <br>Class Key only provides a subset of a US-QWERTY PC keyboard layout
   *
   * @param <PFRML> Pattern, Filename, Text, Region, Match or Location
   * @param target Pattern, Filename, Text, Region, Match or Location
   * @param text containing characters and/or Key constants
   * @param modifiers constants according to class Key - combine using +
   * @return 1 if possible, 0 otherwise
   * @throws FindFailed if not found
   */
  public <PFRML> int type(PFRML target, String text, String modifiers) throws FindFailed {
    int modifiersNew = Key.convertModifiers(modifiers);
    return keyin(target, text, modifiersNew);
  }

  private <PFRML> int keyin(PFRML target, String text, int modifiers)
      throws FindFailed {
    if (target != null && 0 == click(target, 0)) {
      return 0;
    }
    Debug profiler = Debug.startTimer("Region.type");
    if (text != null && !"".equals(text)) {
      String showText = "";
      for (int i = 0; i < text.length(); i++) {
        showText += Key.toJavaKeyCodeText(text.charAt(i));
      }
      String modText = "";
      String modWindows = null;
      if ((modifiers & KeyModifier.WIN) != 0) {
        modifiers -= KeyModifier.WIN;
        modifiers |= KeyModifier.META;
        log(lvl, "Key.WIN as modifier");
        modWindows = "Windows";
      }
      if (modifiers != 0) {
        modText = String.format("( %s ) ", KeyEvent.getKeyModifiersText(modifiers));
        if (modWindows != null) {
          modText = modText.replace("Meta", modWindows);
        }
      }
      Debug.action("%s TYPE \"%s\"", modText, showText);
      log(lvl, "%s TYPE \"%s\"", modText, showText);
      profiler.lap("before getting Robot");
      IRobot r = getRobotForRegion();
      int pause = 20 + (Settings.TypeDelay > 1 ? 1000 : (int) (Settings.TypeDelay * 1000));
      Settings.TypeDelay = 0.0;
      profiler.lap("before typing");
      r.typeStarts();
      for (int i = 0; i < text.length(); i++) {
        r.pressModifiers(modifiers);
        r.typeChar(text.charAt(i), IRobot.KeyMode.PRESS_RELEASE);
        r.releaseModifiers(modifiers);
        r.delay(pause);
      }
      r.typeEnds();
      profiler.lap("after typing, before waitForIdle");
      r.waitForIdle();
      profiler.end();
      return 1;
    }

    return 0;
  }

  /**
   * time in milliseconds to delay between each character at next type only (max 1000)
   *
   * @param millisecs value
   */
  public void delayType(int millisecs) {
    Settings.TypeDelay = millisecs;
  }

  /**
   * pastes the text at the current position of the focus/carret <br>using the clipboard and strg/ctrl/cmd-v (paste
   * keyboard shortcut)
   *
   * @param text a string, which might contain unicode characters
   * @return 0 if possible, 1 otherwise
   */
  public int paste(String text) {
    try {
      return paste(null, text);
    } catch (FindFailed ex) {
      return 1;
    }
  }

  /**
   * first does a click(target) at the given target position to gain focus/carret <br> and then pastes the text <br>
   * using the clipboard and strg/ctrl/cmd-v (paste keyboard shortcut)
   *
   * @param <PFRML> Pattern, Filename, Text, Region, Match or Location target
   * @param target Pattern, Filename, Text, Region, Match or Location
   * @param text a string, which might contain unicode characters
   * @return 0 if possible, 1 otherwise
   * @throws FindFailed if not found
   */
  public <PFRML> int paste(PFRML target, String text) throws FindFailed {
    if (target != null) {
      click(target, 0);
    }
    if (text != null) {
      App.setClipboard(text);
      int mod = Key.getHotkeyModifier();
      IRobot r = getRobotForRegion();
      r.keyDown(mod);
      r.keyDown(KeyEvent.VK_V);
      r.keyUp(KeyEvent.VK_V);
      r.keyUp(mod);
      return 1;
    }
    return 0;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="OCR - read text from Screen">
  /**
   * STILL EXPERIMENTAL: tries to read the text in this region<br> might contain misread characters, NL characters and
   * other stuff, when interpreting contained grafics as text<br>
   * Best results: one line of text with no grafics in the line
   *
   * @return the text read (utf8 encoded)
   */
  public String text() {
    if (Settings.OcrTextRead) {
      ScreenImage simg = getScreen().capture(x, y, w, h);
      TextRecognizer tr = TextRecognizer.getInstance();
      if (tr == null) {
        Debug.error("text: text recognition is now switched off");
        return "--- no text ---";
      }
      String textRead = tr.recognize(simg);
      log(lvl, "text: #(" + textRead + ")#");
      return textRead;
    }
    Debug.error("text: text recognition is currently switched off");
    return "--- no text ---";
  }

  /**
   * VERY EXPERIMENTAL: returns a list of matches, that represent single words, that have been found in this region<br>
   * the match's x,y,w,h the region of the word<br> Match.getText() returns the word (utf8) at this match<br>
   * Match.getScore() returns a value between 0 ... 1, that represents some OCR-confidence value<br > (the higher, the
   * better the OCR engine thinks the result is)
   *
   * @return a list of matches
   */
  public List<Match> listText() {
    if (Settings.OcrTextRead) {
      ScreenImage simg = getScreen().capture(x, y, w, h);
      TextRecognizer tr = TextRecognizer.getInstance();
      if (tr == null) {
        Debug.error("text: text recognition is now switched off");
        return null;
      }
      log(lvl, "listText: scanning %s", this);
      return null; //tr.listText(simg, this);
    }
    Debug.error("text: text recognition is currently switched off");
    return null;
  }
  //</editor-fold>

  public String saveScreenCapture() {
    return getScreen().capture(this).save();
  }

  public String saveScreenCapture(String path) {
    return getScreen().capture(this).save(path);
  }

  public String saveScreenCapture(String path, String name) {
    return getScreen().capture(this).save(path, name);
  }
  
  public String save(String name) {
    return new Image(captureThis()).save(name);
  }
}
