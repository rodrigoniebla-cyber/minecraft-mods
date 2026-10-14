package net.manaz.vtp.gui;

public enum VtpTheme {
   VANILLA(
      "Vanilla+",
      -1610612736,
      -14935012,
      -15461356,
      -14540254,
      -13882324,
      -11184811,
      -171,
      -1,
      -5592406,
      -15329770,
      -12961222,
      -13882324,
      -13092808,
      -14408668,
      -14009832,
      -43691,
      -1072162792,
      -802937820,
      -14405608
   ),
   MODERN(
      "Modern",
      -1073411566,
      -16051168,
      -16380908,
      -15655634,
      -15260616,
      -14929840,
      -13708142,
      -3286808,
      -10718838,
      -16315112,
      -16040664,
      -15983056,
      -15327184,
      -15918808,
      -16040664,
      -2075307,
      -804184786,
      -15260616,
      -15781856
   ),
   TRANSPARENT(
      "Transparent",
      671088640,
      -2013265920,
      -1795162112,
      1879048192,
      Integer.MIN_VALUE,
      -1870100344,
      -171,
      -1,
      -2236963,
      1610612736,
      -1876793822,
      1613968179,
      1885759078,
      1610612736,
      -2145233630,
      -43691,
      0,
      0,
      0,
      true
   ),
   NETHER(
      "Nether",
      -1340996604,
      -15071226,
      -15858685,
      -14020596,
      -12969966,
      -10607592,
      -24512,
      -993080,
      -6260632,
      -15464956,
      -10871280,
      -13757940,
      -12969966,
      -14545912,
      -10868716,
      -40864,
      -1072035834,
      -802549748,
      -11919856
   ),
   OCEAN(
      "Ocean",
      -1610413544,
      -16115672,
      -16379872,
      -15784904,
      -15454136,
      -14792592,
      -12529440,
      -2561808,
      -9924440,
      -16379360,
      -15054752,
      -15848896,
      -15388598,
      -15982544,
      -15382440,
      -40864,
      -1073080280,
      -804314056,
      -15316904
   ),
   SUNSET(
      "Sunset",
      -1609562088,
      -14807000,
      -15464416,
      -13886916,
      -12967864,
      -10866584,
      -32592,
      -991000,
      -5207904,
      -15201760,
      -10868648,
      -13888456,
      -12967864,
      -14543826,
      -11526072,
      -40864,
      -1071771608,
      -802416068,
      -11919288
   ),
   FOREST(
      "Forest",
      -1610282492,
      -15853552,
      -16248824,
      -15326184,
      -14930402,
      -13742544,
      -7810976,
      -2037544,
      -7827336,
      -16117238,
      -14137304,
      -15456236,
      -14667744,
      -15589360,
      -14530528,
      -40864,
      -1072818160,
      -803855336,
      -14399456
   ),
   AMOLED(
      "AMOLED",
      -1073741824,
      -16777216,
      -16777216,
      -16250872,
      -15592942,
      -14013910,
      -16719648,
      -1513240,
      -8355712,
      -16777216,
      -14671840,
      -15724528,
      -15066598,
      -16250872,
      -15062502,
      -43691,
      -1073741824,
      -804780024,
      -15062502
   ),
   MONOCHROME(
      "Monochrome",
      -1610612736,
      -1513240,
      -2565928,
      -855310,
      -2302756,
      -7829368,
      -14671840,
      -15724528,
      -10461088,
      -263173,
      -4276546,
      -2039584,
      -4671304,
      -1381654,
      -3618616,
      -5234656,
      -1057951504,
      -790831908,
      -4276546
   );

   public final String displayName;
   public final int overlay;
   public final int win;
   public final int titlebar;
   public final int panel;
   public final int phdr;
   public final int border;
   public final int accent;
   public final int text;
   public final int muted;
   public final int listBg;
   public final int listSel;
   public final int listHov;
   public final int div;
   public final int lvlBg;
   public final int lvlCur;
   public final int error;
   public final int hudPanel;
   public final int hudPhdr;
   public final int hudMatchBg;
   public final boolean hudNoBackground;
   private static VtpTheme current = VANILLA;

   private VtpTheme(
      String displayName,
      int overlay,
      int win,
      int titlebar,
      int panel,
      int phdr,
      int border,
      int accent,
      int text,
      int muted,
      int listBg,
      int listSel,
      int listHov,
      int div,
      int lvlBg,
      int lvlCur,
      int error,
      int hudPanel,
      int hudPhdr,
      int hudMatchBg
   ) {
      this(
         displayName,
         overlay,
         win,
         titlebar,
         panel,
         phdr,
         border,
         accent,
         text,
         muted,
         listBg,
         listSel,
         listHov,
         div,
         lvlBg,
         lvlCur,
         error,
         hudPanel,
         hudPhdr,
         hudMatchBg,
         false
      );
   }

   private VtpTheme(
      String displayName,
      int overlay,
      int win,
      int titlebar,
      int panel,
      int phdr,
      int border,
      int accent,
      int text,
      int muted,
      int listBg,
      int listSel,
      int listHov,
      int div,
      int lvlBg,
      int lvlCur,
      int error,
      int hudPanel,
      int hudPhdr,
      int hudMatchBg,
      boolean hudNoBackground
   ) {
      this.displayName = displayName;
      this.overlay = overlay;
      this.win = win;
      this.titlebar = titlebar;
      this.panel = panel;
      this.phdr = phdr;
      this.border = border;
      this.accent = accent;
      this.text = text;
      this.muted = muted;
      this.listBg = listBg;
      this.listSel = listSel;
      this.listHov = listHov;
      this.div = div;
      this.lvlBg = lvlBg;
      this.lvlCur = lvlCur;
      this.error = error;
      this.hudPanel = hudPanel;
      this.hudPhdr = hudPhdr;
      this.hudMatchBg = hudMatchBg;
      this.hudNoBackground = hudNoBackground;
   }

   public static VtpTheme current() {
      return current;
   }

   public static void set(VtpTheme t) {
      current = t;
   }

   public static VtpTheme cycle() {
      current = values()[(current.ordinal() + 1) % values().length];
      return current;
   }

   public static VtpTheme fromName(String name) {
      for (VtpTheme t : values()) {
         if (t.name().equalsIgnoreCase(name)) {
            return t;
         }
      }

      return VANILLA;
   }
}
