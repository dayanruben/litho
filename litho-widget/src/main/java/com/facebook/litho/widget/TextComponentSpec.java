/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.litho.widget;

import static androidx.customview.widget.ExploreByTouchHelper.INVALID_ID;
import static com.facebook.litho.SizeSpec.AT_MOST;
import static com.facebook.litho.SizeSpec.EXACTLY;
import static com.facebook.litho.SizeSpec.UNSPECIFIED;
import static com.facebook.litho.widget.TextAlignment.CENTER;
import static com.facebook.litho.widget.TextAlignment.TEXT_END;
import static com.facebook.litho.widget.TextAlignment.TEXT_START;
import static com.facebook.litho.widget.TextStylesHelper.DEFAULT_BREAK_STRATEGY;
import static com.facebook.litho.widget.TextStylesHelper.DEFAULT_EMS;
import static com.facebook.litho.widget.TextStylesHelper.DEFAULT_HYPHENATION_FREQUENCY;
import static com.facebook.litho.widget.TextStylesHelper.DEFAULT_JUSTIFICATION_MODE;
import static com.facebook.litho.widget.TextStylesHelper.DEFAULT_MAX_WIDTH;
import static com.facebook.litho.widget.TextStylesHelper.DEFAULT_MIN_WIDTH;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.Layout.Alignment;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.view.View;
import androidx.annotation.Dimension;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.text.TextDirectionHeuristicCompat;
import androidx.core.text.TextDirectionHeuristicsCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.facebook.fbui.textlayoutbuilder.TextLayoutBuilder;
import com.facebook.fbui.textlayoutbuilder.util.LayoutMeasureUtil;
import com.facebook.litho.AccessibilityRole;
import com.facebook.litho.Component;
import com.facebook.litho.ComponentContext;
import com.facebook.litho.ComponentLayout;
import com.facebook.litho.ComponentsReporter;
import com.facebook.litho.EventHandler;
import com.facebook.litho.Output;
import com.facebook.litho.Size;
import com.facebook.litho.SizeSpec;
import com.facebook.litho.annotations.FromBoundsDefined;
import com.facebook.litho.annotations.FromMeasure;
import com.facebook.litho.annotations.GetExtraAccessibilityNodeAt;
import com.facebook.litho.annotations.GetExtraAccessibilityNodesCount;
import com.facebook.litho.annotations.MountSpec;
import com.facebook.litho.annotations.OnBindDynamicValue;
import com.facebook.litho.annotations.OnBoundsDefined;
import com.facebook.litho.annotations.OnCreateMountContent;
import com.facebook.litho.annotations.OnLoadStyle;
import com.facebook.litho.annotations.OnMeasure;
import com.facebook.litho.annotations.OnMount;
import com.facebook.litho.annotations.OnPerformActionForVirtualView;
import com.facebook.litho.annotations.OnPopulateAccessibilityNode;
import com.facebook.litho.annotations.OnPopulateExtraAccessibilityNode;
import com.facebook.litho.annotations.OnUnmount;
import com.facebook.litho.annotations.OnVirtualViewKeyboardFocusChanged;
import com.facebook.litho.annotations.Prop;
import com.facebook.litho.annotations.PropDefault;
import com.facebook.litho.annotations.ResType;
import com.facebook.litho.config.ComponentsConfiguration;
import com.facebook.litho.utils.VersionedAndroidApis;
import com.facebook.rendercore.text.ClickableSpanListener;
import com.facebook.rendercore.text.TouchableSpanListener;
import com.facebook.widget.accessibility.delegates.AccessibleClickableSpan;
import com.facebook.widget.accessibility.delegates.ContentDescriptionSpan;
import com.facebook.yoga.YogaDirection;
import java.util.Collections;
import java.util.Objects;

/**
 * Component to render text. See <a href="https://fblitho.com/docs/widgets#text">text-widget</a> for
 * more details.
 *
 * <p>Example Text usage:
 *
 * <pre>{@code
 * final SpannableStringBuilder spannable = new SpannableStringBuilder();
 * spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
 *
 * Text.create(c)
 *    .text(spannable) // String can be used
 *    .textSizeDip(20)
 *    .maxLines(3)
 *    .ellipsize(TextUtils.TruncateAt.END)
 *    .textColor(Color.BLACK)
 *    .build()
 * }</pre>
 *
 * @uidocs
 * @prop text Text to display.
 * @prop ellipsize If set, specifies the position of the text to be ellipsized.
 * @prop minLines Minimum number of lines to show.
 * @prop maxLines Maximum number of lines to show.
 * @prop minEms Makes the text to be mim ems wide.
 * @prop maxEms Makes the text to be max ems wide.
 * @prop minTextWidth Makes the text to be min pixels wide.
 * @prop maxTextWidth Makes the text to be max pixels wide.
 * @prop shadowRadius Blur radius of the shadow.
 * @prop shadowDx Horizontal offset of the shadow.
 * @prop shadowDy Vertical offset of the shadow.
 * @prop shadowColor Color for the shadow underneath the text.
 * @prop outlineWidth If set, gives the text outline of the specified width.
 * @prop outlineColor Sets the outline color; if it's 0 or not set, outline uses the same color as
 *     shadow.
 * @prop isSingleLine If set, makes the text to be rendered in a single line.
 * @prop textColor Color of the text.
 * @prop textColorStateList ColorStateList of the text.
 * @prop linkColor Color for links in the text.
 * @prop highlightColor Color for an optional highlight of the text.
 * @prop highlightStartOffset Start offset for an optional highlight of the text.
 * @prop highlightEndOffset End offset for an optional highlight of the text.
 * @prop textSize Size of the text.
 * @prop extraSpacing Extra spacing between the lines of text.
 * @prop spacingMultiplier Extra spacing between the lines of text, as a multiplier.
 * @prop letterSpacing Text letter-spacing. Typical values for slight expansion will be around 0.05
 *     ems. Negative values tighten text.
 * @prop textStyle Style for the font (e.g. {@link Typeface.BOLD}). See the @Style interface in
 *     {@link Typeface} for supported attributes.
 * @prop typeface Typeface for the text.
 * @prop textAlignment Alignment of the text within its container.
 * @prop breakStrategy Break strategy to use for multi-line text.
 * @prop hyphenationFrequency How frequently to hyphenate text.
 * @prop justificationMode How to justify the text. See {@link android.text.Layout}
 * @prop glyphWarming If set, pre-renders the text to an off-screen Canvas to boost performance.
 * @prop textDirection Heuristic to use to determine the direction of the text.
 * @prop shouldIncludeFontPadding If set, uses extra padding for ascenders and descenders.
 * @prop verticalGravity Vertical gravity for the text within its container.
 * @prop clickableSpanExpandedOffset Click offset amount to determine how far off the ClickableSpan
 *     bounds user can click to be able to trigger ClickableSpan's click action. This could be
 *     useful in a densely lined text with links like 'Continue reading ...' in NewsFeed to be able
 *     to click that easily.
 * @prop spanListener Listener to override click and/or longclick actions of spannables extracted
 *     from text. This can be used to avoid memory leaks if the click/long click actions require a
 *     context, since spannables are stored statically in memory.
 * @prop touchableSpanListener Listener to listen for touch down actions of spannables extracted
 *     from text. This can be used to avoid memory leaks if the touch down actions require a
 *     context, since spannables are stored statically in memory.
 * @prop clipToBounds If the text should be clipped inside component bounds. Default: {@code true}
 * @prop customEllipsisText Text used to replace the standard Android ... ellipsis at the end of
 *     truncated lines. Warning: specifying this prop causes measurement to run twice. This can have
 *     a serious performance cost, especially on older devices!
 * @prop textOffsetOnTouchHandler A handler for touch events that need to know their character
 *     offset into the text. Will only fire on ACTION_DOWN events that occur at an index within the
 *     text.
 * @prop accessibleClickableSpans Whether the text can contain accessible clickable spans.
 * @prop minimallyWide If set, multi-line text width is determined by the widest line, rather than
 *     the overall layout width. This can eliminate empty space in word-wrapped text with line
 *     breaks preceding lengthy words or spans.
 * @prop minimallyWideThreshold If set, {@code minimallyWide} logic will not run for text whose
 *     minimal width is smaller than its normal width by less than the threshold.
 * @prop lineHeight Controls the line height of text (the amount of vertical space reserved for each
 *     line of text).
 */
@MountSpec(
    isPureRender = true,
    poolSize = 30,
    canPreallocate = true,
    events = {TextOffsetOnTouchEvent.class})
public class TextComponentSpec {

  public static final @Dimension(unit = Dimension.SP) int DEFAULT_TEXT_SIZE_SP = 14;
  public static final int UNSET = -1;

  public static final Typeface DEFAULT_TYPEFACE = Typeface.DEFAULT;
  public static final int DEFAULT_COLOR = 0;
  private static final String TAG = "TextComponentSpec";
  private static final String WRONG_TEXT_SIZE = "TextComponentSpec:WrongTextSize";

  public static final int[][] DEFAULT_TEXT_COLOR_STATE_LIST_STATES = {{0}};
  public static final int[] DEFAULT_TEXT_COLOR_STATE_LIST_COLORS = {Color.BLACK};

  @PropDefault protected static final int minLines = Integer.MIN_VALUE;
  @PropDefault protected static final int maxLines = Integer.MAX_VALUE;
  @PropDefault protected static final int minEms = DEFAULT_EMS;
  @PropDefault protected static final int maxEms = DEFAULT_EMS;
  @PropDefault protected static final int minTextWidth = DEFAULT_MIN_WIDTH;
  @PropDefault protected static final int maxTextWidth = DEFAULT_MAX_WIDTH;
  @PropDefault protected static final int shadowColor = Color.GRAY;
  @PropDefault protected static final float outlineWidth = 0f;
  @PropDefault protected static final int outlineColor = 0;
  @PropDefault protected static final int textColor = DEFAULT_COLOR;
  @PropDefault protected static final int linkColor = Color.BLUE;

  @PropDefault
  protected static final ColorStateList textColorStateList =
      new ColorStateList(
          DEFAULT_TEXT_COLOR_STATE_LIST_STATES, DEFAULT_TEXT_COLOR_STATE_LIST_COLORS);

  @PropDefault protected static final int textSize = UNSET;
  @PropDefault protected static final int textStyle = DEFAULT_TYPEFACE.getStyle();
  @PropDefault protected static final Typeface typeface = DEFAULT_TYPEFACE;
  @PropDefault protected static final float spacingMultiplier = 1.0f;
  @PropDefault protected static final VerticalGravity verticalGravity = VerticalGravity.TOP;
  @PropDefault protected static final boolean glyphWarming = false;
  @PropDefault protected static final boolean shouldIncludeFontPadding = true;

  @PropDefault protected static final int breakStrategy = DEFAULT_BREAK_STRATEGY;
  @PropDefault protected static final int hyphenationFrequency = DEFAULT_HYPHENATION_FREQUENCY;
  @PropDefault protected static final int justificationMode = DEFAULT_JUSTIFICATION_MODE;
  @PropDefault protected static final int highlightStartOffset = -1;
  @PropDefault protected static final int highlightEndOffset = -1;
  @PropDefault protected static final boolean clipToBounds = true;
  @PropDefault protected static final float lineHeight = Float.MAX_VALUE;

  private static final Path sTempPath = new Path();
  private static final Rect sTempRect = new Rect();
  private static final RectF sTempRectF = new RectF();

  @OnLoadStyle
  static void onLoadStyle(
      ComponentContext c,
      Output<TruncateAt> ellipsize,
      Output<Float> extraSpacing,
      Output<Boolean> shouldIncludeFontPadding,
      Output<Float> spacingMultiplier,
      Output<Integer> minLines,
      Output<Integer> maxLines,
      Output<Integer> minEms,
      Output<Integer> maxEms,
      Output<Integer> minTextWidth,
      Output<Integer> maxTextWidth,
      Output<Boolean> isSingleLine,
      Output<CharSequence> text,
      Output<ColorStateList> textColorStateList,
      Output<Integer> linkColor,
      Output<Integer> highlightColor,
      Output<Integer> textSize,
      Output<TextAlignment> alignment,
      Output<Integer> breakStrategy,
      Output<Integer> hyphenationFrequency,
      Output<Integer> justificationMode,
      Output<Integer> textStyle,
      Output<Float> shadowRadius,
      Output<Float> shadowDx,
      Output<Float> shadowDy,
      Output<Integer> shadowColor,
      Output<VerticalGravity> verticalGravity,
      Output<Typeface> typeface) {

    TextStylesHelper.onLoadStyle(
        c,
        ellipsize,
        extraSpacing,
        shouldIncludeFontPadding,
        spacingMultiplier,
        minLines,
        maxLines,
        minEms,
        maxEms,
        minTextWidth,
        maxTextWidth,
        isSingleLine,
        text,
        textColorStateList,
        linkColor,
        highlightColor,
        textSize,
        alignment,
        breakStrategy,
        hyphenationFrequency,
        justificationMode,
        textStyle,
        shadowRadius,
        shadowDx,
        shadowDy,
        shadowColor,
        verticalGravity,
        typeface);
  }

  @OnMeasure
  static void onMeasure(
      ComponentContext context,
      ComponentLayout layout,
      int widthSpec,
      int heightSpec,
      Size size,
      @Prop(resType = ResType.STRING) @Nullable CharSequence text,
      @Nullable @Prop(optional = true) TruncateAt ellipsize,
      @Prop(optional = true, resType = ResType.BOOL) boolean shouldIncludeFontPadding,
      @Prop(optional = true, resType = ResType.INT) int minLines,
      @Prop(optional = true, resType = ResType.INT) int maxLines,
      @Prop(optional = true, resType = ResType.INT) int minEms,
      @Prop(optional = true, resType = ResType.INT) int maxEms,
      @Prop(optional = true, resType = ResType.DIMEN_SIZE) int minTextWidth,
      @Prop(optional = true, resType = ResType.DIMEN_SIZE) int maxTextWidth,
      @Prop(optional = true, resType = ResType.DIMEN_OFFSET) float shadowRadius,
      @Prop(optional = true, resType = ResType.DIMEN_OFFSET) float shadowDx,
      @Prop(optional = true, resType = ResType.DIMEN_OFFSET) float shadowDy,
      @Prop(optional = true, resType = ResType.COLOR) int shadowColor,
      @Prop(optional = true, resType = ResType.BOOL) boolean isSingleLine,
      @Prop(optional = true, resType = ResType.COLOR) int textColor,
      @Prop(optional = true) ColorStateList textColorStateList,
      @Prop(optional = true, resType = ResType.COLOR) int linkColor,
      @Prop(optional = true, resType = ResType.DIMEN_TEXT) int textSize,
      @Prop(optional = true, resType = ResType.DIMEN_OFFSET) float extraSpacing,
      @Prop(optional = true, resType = ResType.FLOAT) float spacingMultiplier,
      @Prop(optional = true, resType = ResType.FLOAT) float letterSpacing,
      @Prop(optional = true) int textStyle,
      @Prop(optional = true) @Nullable Typeface typeface,
      @Prop(optional = true) @Nullable @Deprecated Alignment textAlignment,
      @Nullable @Prop(optional = true) TextAlignment alignment,
      @Prop(optional = true) int breakStrategy,
      @Prop(optional = true) int hyphenationFrequency,
      @Prop(optional = true) int justificationMode,
      @Prop(optional = true) boolean glyphWarming,
      @Nullable @Prop(optional = true) TextDirectionHeuristicCompat textDirection,
      @Prop(optional = true) boolean minimallyWide,
      @Prop(optional = true, resType = ResType.DIMEN_SIZE) int minimallyWideThreshold,
      @Prop(optional = true, resType = ResType.DIMEN_TEXT) float lineHeight,
      Output<Layout> measureLayout,
      Output<Integer> measuredWidth,
      Output<Integer> measuredHeight,
      Output<Integer> fullWidth) {

    if (TextUtils.isEmpty(text)) {
      measureLayout.set(null);
      size.width = 0;
      size.height = 0;
      return;
    }

    TextAlignment resolvedTextAlignment = getTextAlignment(textAlignment, alignment);

    Layout newLayout =
        createTextLayout(
            context,
            widthSpec,
            ellipsize,
            shouldIncludeFontPadding,
            maxLines,
            shadowRadius,
            shadowDx,
            shadowDy,
            shadowColor,
            isSingleLine,
            text,
            textColor,
            textColorStateList,
            linkColor,
            textSize,
            extraSpacing,
            spacingMultiplier,
            letterSpacing,
            textStyle,
            typeface,
            resolvedTextAlignment,
            glyphWarming,
            layout.getResolvedLayoutDirection(),
            minEms,
            maxEms,
            minTextWidth,
            maxTextWidth,
            context.getAndroidContext().getResources().getDisplayMetrics().density,
            breakStrategy,
            hyphenationFrequency,
            justificationMode,
            textDirection,
            lineHeight);
    measureLayout.set(newLayout);

    fullWidth.set(Math.max(0, SizeSpec.resolveSize(widthSpec, newLayout.getWidth())));
    size.width = resolveWidth(widthSpec, newLayout, minimallyWide, minimallyWideThreshold);

    // Adjust height according to the minimum number of lines.
    int preferredHeight = LayoutMeasureUtil.getHeight(newLayout);
    final int lineCount = newLayout.getLineCount();
    if (lineCount < minLines) {
      final TextPaint paint = newLayout.getPaint();

      final int layoutLineHeight =
          Math.round(paint.getFontMetricsInt(null) * spacingMultiplier + extraSpacing);
      preferredHeight += layoutLineHeight * (minLines - lineCount);
    }

    size.height = SizeSpec.resolveSize(heightSpec, preferredHeight);

    // Some devices seem to be returning negative sizes in some cases.
    if (size.width < 0 || size.height < 0) {
      size.width = Math.max(size.width, 0);
      size.height = Math.max(size.height, 0);

      ComponentsReporter.emitMessage(
          ComponentsReporter.LogLevel.ERROR,
          WRONG_TEXT_SIZE,
          "Text layout measured to less than 0 pixels");
    }

    measuredWidth.set(size.width);
    measuredHeight.set(size.height);
  }

  @VisibleForTesting
  public static int resolveWidth(
      int widthSpec, Layout layout, boolean minimallyWide, int minimallyWideThreshold) {
    final int fullWidth = SizeSpec.resolveSize(widthSpec, layout.getWidth());

    if (minimallyWide && layout.getLineCount() > 1) {
      float leftMost = fullWidth;
      float rightMost = 0;
      for (int i = 0, count = layout.getLineCount(); i < count; i++) {
        leftMost = Math.min(leftMost, layout.getLineLeft(i));
        rightMost = Math.max(rightMost, layout.getLineRight(i));
      }
      // To determine the width of the longest line, which is also the minimum width we desire,
      // without leading and trailing whitespaces.
      final int minimalWidth = SizeSpec.resolveSize(widthSpec, (int) (rightMost - leftMost));

      if (fullWidth - minimalWidth > minimallyWideThreshold) {
        return minimalWidth;
      }
    }

    return fullWidth;
  }

  private static Layout createTextLayout(
      ComponentContext context,
      int widthSpec,
      @Nullable TruncateAt ellipsize,
      boolean shouldIncludeFontPadding,
      int maxLines,
      float shadowRadius,
      float shadowDx,
      float shadowDy,
      int shadowColor,
      boolean isSingleLine,
      CharSequence text,
      int textColor,
      ColorStateList textColorStateList,
      int linkColor,
      int textSize,
      float extraSpacing,
      float spacingMultiplier,
      float letterSpacing,
      int textStyle,
      Typeface typeface,
      TextAlignment textAlignment,
      boolean glyphWarming,
      YogaDirection layoutDirection,
      int minEms,
      int maxEms,
      int minTextWidth,
      int maxTextWidth,
      float density,
      int breakStrategy,
      int hyphenationFrequency,
      int justificationMode,
      @Nullable TextDirectionHeuristicCompat textDirection,
      float lineHeight) {
    Layout newLayout;

    TextLayoutBuilder layoutBuilder = new TextLayoutBuilder();
    layoutBuilder.setShouldCacheLayout(false);

    @TextLayoutBuilder.MeasureMode final int textMeasureMode;
    switch (SizeSpec.getMode(widthSpec)) {
      case UNSPECIFIED:
        textMeasureMode = TextLayoutBuilder.MEASURE_MODE_UNSPECIFIED;
        break;
      case EXACTLY:
        textMeasureMode = TextLayoutBuilder.MEASURE_MODE_EXACTLY;
        break;
      case AT_MOST:
        textMeasureMode = TextLayoutBuilder.MEASURE_MODE_AT_MOST;
        break;
      default:
        throw new IllegalStateException("Unexpected size mode: " + SizeSpec.getMode(widthSpec));
    }

    final TruncateAt actualEllipsize;
    if (ellipsize == null && maxLines != Integer.MAX_VALUE) {
      // On recent apis (> 24) max lines is no longer considered for calculating layout height if an
      // ellipsize method isn't specified. To keep consistent behavior across platforms we default
      // to end if you specify maxLines but not ellipsize.
      actualEllipsize = TruncateAt.END;
    } else {
      actualEllipsize = ellipsize;
    }

    layoutBuilder
        .setDensity(density)
        .setEllipsize(actualEllipsize)
        .setMaxLines(maxLines)
        .setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowColor)
        .setSingleLine(isSingleLine)
        .setText(text)
        .setWidth(SizeSpec.getSize(widthSpec), textMeasureMode)
        .setIncludeFontPadding(shouldIncludeFontPadding)
        .setTextSpacingExtra(extraSpacing)
        .setTextSpacingMultiplier(spacingMultiplier)
        .setLinkColor(linkColor)
        .setJustificationMode(justificationMode)
        .setBreakStrategy(breakStrategy)
        .setHyphenationFrequency(hyphenationFrequency);

    // text size must be set before the line hight
    if (textSize != UNSET) {
      layoutBuilder.setTextSize(textSize);
    } else {
      int defaultTextSize = context.getResourceResolver().sipsToPixels(DEFAULT_TEXT_SIZE_SP);
      layoutBuilder.setTextSize(defaultTextSize);
    }

    if (lineHeight != Float.MAX_VALUE) {
      layoutBuilder.setLineHeight(lineHeight);
    }

    layoutBuilder.setLetterSpacing(letterSpacing);

    if (minEms != DEFAULT_EMS) {
      layoutBuilder.setMinEms(minEms);
    } else {
      layoutBuilder.setMinWidth(minTextWidth);
    }

    if (maxEms != DEFAULT_EMS) {
      layoutBuilder.setMaxEms(maxEms);
    } else {
      layoutBuilder.setMaxWidth(maxTextWidth);
    }

    if (textColor != TextComponentSpec.DEFAULT_COLOR) {
      layoutBuilder.setTextColor(textColor);
    } else {
      layoutBuilder.setTextColor(textColorStateList);
    }

    if (!DEFAULT_TYPEFACE.equals(typeface)) {
      layoutBuilder.setTypeface(typeface);
    } else {
      layoutBuilder.setTextStyle(textStyle);
    }

    textDirection = getTextDirection(textDirection, layoutDirection);
    layoutBuilder.setTextDirection(textDirection);
    layoutBuilder.setAlignment(
        getLayoutAlignment(textAlignment, textDirection, text, layoutDirection));

    try {
      newLayout = layoutBuilder.build();
    } catch (ArrayIndexOutOfBoundsException e) { // To capture more info for T102756635
      throw new RuntimeException("text: " + text.toString(), e);
    }

    if (glyphWarming) {
      // TODO(T34488162): we also don't want this to happen when we are using DL (legacy?)
      TextureWarmer.getInstance().warmLayout(newLayout);
    }

    return newLayout;
  }

  @OnBoundsDefined
  static void onBoundsDefined(
      ComponentContext c,
      ComponentLayout layout,
      @Prop(resType = ResType.STRING) CharSequence text,
      @Nullable @Prop(optional = true) TruncateAt ellipsize,
      @Prop(optional = true, resType = ResType.BOOL) boolean shouldIncludeFontPadding,
      @Prop(optional = true, resType = ResType.INT) int maxLines,
      @Prop(optional = true, resType = ResType.INT) int minEms,
      @Prop(optional = true, resType = ResType.INT) int maxEms,
      @Prop(optional = true, resType = ResType.DIMEN_SIZE) int minTextWidth,
      @Prop(optional = true, resType = ResType.DIMEN_SIZE) int maxTextWidth,
      @Prop(optional = true, resType = ResType.DIMEN_OFFSET) float shadowRadius,
      @Prop(optional = true, resType = ResType.DIMEN_OFFSET) float shadowDx,
      @Prop(optional = true, resType = ResType.DIMEN_OFFSET) float shadowDy,
      @Prop(optional = true, resType = ResType.COLOR) int shadowColor,
      @Prop(optional = true, resType = ResType.BOOL) boolean isSingleLine,
      @Prop(optional = true, resType = ResType.COLOR) int textColor,
      @Prop(optional = true) ColorStateList textColorStateList,
      @Prop(optional = true, resType = ResType.COLOR) int linkColor,
      @Prop(optional = true, resType = ResType.DIMEN_TEXT) int textSize,
      @Prop(optional = true, resType = ResType.DIMEN_OFFSET) float extraSpacing,
      @Prop(optional = true, resType = ResType.FLOAT) float spacingMultiplier,
      @Prop(optional = true, resType = ResType.FLOAT) float letterSpacing,
      @Prop(optional = true) VerticalGravity verticalGravity,
      @Prop(optional = true) int textStyle,
      @Prop(optional = true) Typeface typeface,
      @Prop(optional = true) @Nullable @Deprecated Alignment textAlignment,
      @Nullable @Prop(optional = true) TextAlignment alignment,
      @Prop(optional = true) int breakStrategy,
      @Prop(optional = true) int hyphenationFrequency,
      @Prop(optional = true) boolean glyphWarming,
      @Nullable @Prop(optional = true) TextDirectionHeuristicCompat textDirection,
      @Nullable @Prop(optional = true, resType = ResType.STRING) CharSequence customEllipsisText,
      @Prop(optional = true, resType = ResType.DIMEN_TEXT) float lineHeight,
      @Prop(optional = true) boolean minimallyWide,
      @FromMeasure Layout measureLayout,
      @FromMeasure Integer measuredWidth,
      @FromMeasure Integer measuredHeight,
      @FromMeasure Integer fullWidth,
      Output<CharSequence> processedText,
      Output<Layout> textLayout,
      Output<Float> textLayoutTranslationX,
      Output<Float> textLayoutTranslationY,
      Output<ClickableSpan[]> clickableSpans,
      Output<ImageSpan[]> imageSpans) {

    processedText.set(text);
    if (TextUtils.isEmpty(text)) {
      return;
    }

    final float layoutWidth =
        layout.getWidth() - layout.getPaddingLeft() - layout.getPaddingRight();
    final float layoutHeight =
        layout.getHeight() - layout.getPaddingTop() - layout.getPaddingBottom();

    if (measureLayout != null && measuredWidth == layoutWidth && measuredHeight == layoutHeight) {
      textLayout.set(measureLayout);
      // We don't need to perform translation if we didn't pass minimally wide threshold above
      if (minimallyWide && !Objects.equals(fullWidth, measuredWidth)) {
        // Regardless of the text alignment, we can always use the leftmost point (the longest line)
        // as our starting point to keep the text drawable center-aligned.
        float leftMost = measuredWidth;
        for (int i = 0, count = measureLayout.getLineCount(); i < count; i++) {
          leftMost = Math.min(leftMost, measureLayout.getLineLeft(i));
        }
        textLayoutTranslationX.set(-leftMost);
      }
    } else {
      textLayout.set(
          createTextLayout(
              c,
              SizeSpec.makeSizeSpec((int) layoutWidth, EXACTLY),
              ellipsize,
              shouldIncludeFontPadding,
              maxLines,
              shadowRadius,
              shadowDx,
              shadowDy,
              shadowColor,
              isSingleLine,
              text,
              textColor,
              textColorStateList,
              linkColor,
              textSize,
              extraSpacing,
              spacingMultiplier,
              letterSpacing,
              textStyle,
              typeface,
              getTextAlignment(textAlignment, alignment),
              glyphWarming,
              layout.getResolvedLayoutDirection(),
              minEms,
              maxEms,
              minTextWidth,
              maxTextWidth,
              c.getAndroidContext().getResources().getDisplayMetrics().density,
              breakStrategy,
              hyphenationFrequency,
              justificationMode,
              textDirection,
              lineHeight));
    }

    final float textHeight = LayoutMeasureUtil.getHeight(textLayout.get());

    switch (verticalGravity) {
      case CENTER:
        textLayoutTranslationY.set((layoutHeight - textHeight) / 2);
        break;

      case BOTTOM:
        textLayoutTranslationY.set(layoutHeight - textHeight);
        break;

      default:
        textLayoutTranslationY.set(0f);
        break;
    }

    // Handle custom text truncation:
    if (customEllipsisText != null && !customEllipsisText.equals("")) {
      final int ellipsizedLineNumber = getEllipsizedLineNumber(textLayout.get());
      if (ellipsizedLineNumber != -1) {
        Layout customEllipsisLayout =
            createTextLayout(
                c,
                SizeSpec.makeSizeSpec((int) layoutWidth, EXACTLY),
                ellipsize,
                shouldIncludeFontPadding,
                maxLines,
                shadowRadius,
                shadowDx,
                shadowDy,
                shadowColor,
                isSingleLine,
                customEllipsisText,
                textColor,
                textColorStateList,
                linkColor,
                textSize,
                extraSpacing,
                spacingMultiplier,
                letterSpacing,
                textStyle,
                typeface,
                getTextAlignment(textAlignment, alignment),
                glyphWarming,
                layout.getResolvedLayoutDirection(),
                minEms,
                maxEms,
                minTextWidth,
                maxTextWidth,
                c.getAndroidContext().getResources().getDisplayMetrics().density,
                breakStrategy,
                hyphenationFrequency,
                justificationMode,
                textDirection,
                lineHeight);

        YogaDirection layoutDirection = layout.getResolvedLayoutDirection();
        TextDirectionHeuristicCompat finalTextDirection =
            getTextDirection(textDirection, layoutDirection);
        Layout.Alignment finalLayoutAlignment = customEllipsisLayout.getAlignment();
        boolean isRtl = finalTextDirection.isRtl(text, 0, text.length());
        boolean isAlignedLeft = isRtl ^ (finalLayoutAlignment == Alignment.ALIGN_NORMAL);
        final CharSequence truncated =
            truncateText(
                text,
                customEllipsisText,
                textLayout.get(),
                customEllipsisLayout,
                ellipsizedLineNumber,
                layoutWidth,
                isAlignedLeft,
                isRtl);

        Layout newLayout =
            createTextLayout(
                c,
                SizeSpec.makeSizeSpec((int) layoutWidth, EXACTLY),
                ellipsize,
                shouldIncludeFontPadding,
                maxLines,
                shadowRadius,
                shadowDx,
                shadowDy,
                shadowColor,
                isSingleLine,
                truncated,
                textColor,
                textColorStateList,
                linkColor,
                textSize,
                extraSpacing,
                spacingMultiplier,
                letterSpacing,
                textStyle,
                typeface,
                getTextAlignment(textAlignment, alignment),
                glyphWarming,
                layout.getResolvedLayoutDirection(),
                minEms,
                maxEms,
                minTextWidth,
                maxTextWidth,
                c.getAndroidContext().getResources().getDisplayMetrics().density,
                breakStrategy,
                hyphenationFrequency,
                justificationMode,
                textDirection,
                lineHeight);

        processedText.set(truncated);
        textLayout.set(newLayout);
      }
    }

    final CharSequence resultText = processedText.get();
    if (resultText instanceof Spanned) {
      Spanned spanned = (Spanned) resultText;
      clickableSpans.set(spanned.getSpans(0, resultText.length(), ClickableSpan.class));
      imageSpans.set(spanned.getSpans(0, resultText.length(), ImageSpan.class));
    }
  }

  /**
   * Truncates text which is too long and appends the given custom ellipsis CharSequence to the end
   * of the visible text.
   *
   * @param text Text to truncate
   * @param customEllipsisText Text to append to the end to indicate truncation happened
   * @param newLayout A Layout object populated with measurement information for this text
   * @param ellipsisTextLayout A Layout object populated with measurement information for the
   *     ellipsis text.
   * @param ellipsizedLineNumber The line number within the text at which truncation occurs (i.e.
   *     the last visible line).
   * @return The provided text truncated in such a way that the 'customEllipsisText' can appear at
   *     the end.
   */
  private static CharSequence truncateText(
      CharSequence text,
      CharSequence customEllipsisText,
      Layout newLayout,
      Layout ellipsisTextLayout,
      int ellipsizedLineNumber,
      float layoutWidth,
      boolean isAlignedLeft,
      boolean isRtl) {
    float customEllipsisTextWidth = ellipsisTextLayout.getLineWidth(0);
    // Identify the X position at which to truncate the final line:
    float ellipsisTarget;
    if (!isRtl && isAlignedLeft) {
      ellipsisTarget = layoutWidth - customEllipsisTextWidth;
    } else if (!isRtl /* && !isAlignedLeft */) {
      final float gap = layoutWidth - newLayout.getLineWidth(ellipsizedLineNumber);
      ellipsisTarget = layoutWidth - customEllipsisTextWidth + gap;
    } else if (
    /* isRtl && */ isAlignedLeft) {
      final float gap = layoutWidth - newLayout.getLineWidth(ellipsizedLineNumber);
      ellipsisTarget = customEllipsisTextWidth - gap;
    } else /* isRtl && !isAlignedLeft */ {
      ellipsisTarget = customEllipsisTextWidth;
    }
    // Get character offset number corresponding to that X position:
    final Paint paint = newLayout.getPaint();
    final int lineStart = newLayout.getLineStart(ellipsizedLineNumber);
    final int lineEnd = newLayout.getLineEnd(ellipsizedLineNumber);
    int ellipsisOffset =
        paint.getOffsetForAdvance(
            text, lineStart, lineEnd, lineStart, lineEnd, isRtl, ellipsisTarget);

    if (ellipsisOffset > 0) {

      // Since the offset adjustment was for the original implementation with
      // [Layout.getOffsetForHorizontal], now we've moved to using [Paint.getOffsetForAdvance] which
      // returns different value from the previous one, so we don't need it anymore.
      if (!ComponentsConfiguration.enableFixForTextEllipsisOffset) {
        // getOffsetForHorizontal returns the closest character, but we need to guarantee no
        // truncation, so subtract 1 from the result:
        ellipsisOffset -= 1;
      }

      // Ensure that we haven't chosen an ellipsisOffset that's past the end of the ellipsis start.
      // This can occur in several cases, including when the width of the customEllipsisText is less
      // than the width of the default ellipsis character, and when in RTL mode and there is
      // whitespace to the left of the text. In these cases, getOffsetForHorizontal will return the
      // end of the string because our ellipsisTarget was in the middle of the ellipsis character.
      if (newLayout.getEllipsisCount(ellipsizedLineNumber) > 0) {
        final int ellipsisStart =
            newLayout.getLineStart(ellipsizedLineNumber)
                + newLayout.getEllipsisStart(ellipsizedLineNumber);
        if (ellipsisOffset > ellipsisStart) {
          ellipsisOffset = ellipsisStart;
        }
      }

      if (ellipsisOffset < 0) {
        ellipsisOffset = 0;
      } else if (ellipsisOffset > text.length()) {
        ellipsisOffset = text.length();
      } else if (Character.isSurrogate(text.charAt(ellipsisOffset))
          && ellipsisOffset != 0
          && ellipsisOffset != text.length()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          ellipsisOffset = VersionedAndroidApis.Q.breakIteratorGetPreceding(text, ellipsisOffset);
        } else {
          java.text.BreakIterator iterator = java.text.BreakIterator.getCharacterInstance();
          iterator.setText(text.toString());
          ellipsisOffset = iterator.preceding(ellipsisOffset);
        }
      }

      return TextUtils.concat(text.subSequence(0, ellipsisOffset), customEllipsisText);
    } else {
      return text;
    }
  }

  /**
   * @param layout A prepared text layout object
   * @return The (zero-indexed) line number at which the text in this layout will be ellipsized, or
   *     -1 if no line will be ellipsized.
   */
  private static int getEllipsizedLineNumber(Layout layout) {
    for (int i = 0; i < layout.getLineCount(); ++i) {
      if (layout.getEllipsisCount(i) > 0) {
        return i;
      }
    }
    return -1;
  }

  @OnCreateMountContent
  public static TextDrawable onCreateMountContent(Context c) {
    return new TextDrawable();
  }

  @OnMount
  static void onMount(
      ComponentContext c,
      TextDrawable textDrawable,
      @Prop(optional = true, resType = ResType.COLOR) int textColor,
      @Prop(optional = true, resType = ResType.COLOR) int highlightColor,
      @Prop(optional = true, resType = ResType.DIMEN_OFFSET) float outlineWidth,
      @Prop(optional = true, resType = ResType.COLOR) int outlineColor,
      @Prop(optional = true) ColorStateList textColorStateList,
      @Nullable @Prop(optional = true) final EventHandler textOffsetOnTouchHandler,
      @Prop(optional = true) int highlightStartOffset,
      @Prop(optional = true) int highlightEndOffset,
      @Prop(optional = true, resType = ResType.DIMEN_TEXT) float clickableSpanExpandedOffset,
      @Prop(optional = true) boolean clipToBounds,
      @Nullable @Prop(optional = true) ClickableSpanListener spanListener,
      @Nullable @Prop(optional = true) TouchableSpanListener touchableSpanListener,
      final @FromBoundsDefined CharSequence processedText,
      @FromBoundsDefined Layout textLayout,
      @Nullable @FromBoundsDefined Float textLayoutTranslationX,
      @FromBoundsDefined Float textLayoutTranslationY,
      @Nullable @FromBoundsDefined ClickableSpan[] clickableSpans,
      @Nullable @FromBoundsDefined ImageSpan[] imageSpans) {
    Component componentScope = c.getComponentScope();
    if (componentScope != null) {
      componentScope.setDebugAttributeKey(
          WidgetAttributes.Text, Collections.singletonList(processedText));
    }

    TextDrawable.TextOffsetOnTouchListener textOffsetOnTouchListener = null;

    if (textOffsetOnTouchHandler != null) {
      textOffsetOnTouchListener =
          new TextDrawable.TextOffsetOnTouchListener() {
            @Override
            public void textOffsetOnTouch(int textOffset) {
              TextComponent.dispatchTextOffsetOnTouchEvent(
                  textOffsetOnTouchHandler, processedText, textOffset);
            }
          };
    }
    textDrawable.mount(
        processedText,
        textLayout,
        textLayoutTranslationX == null ? 0 : textLayoutTranslationX,
        textLayoutTranslationY == null ? 0 : textLayoutTranslationY,
        clipToBounds,
        textColorStateList,
        textColor,
        highlightColor,
        outlineWidth,
        outlineColor,
        clickableSpans,
        imageSpans,
        spanListener,
        touchableSpanListener,
        textOffsetOnTouchListener,
        highlightStartOffset,
        highlightEndOffset,
        clickableSpanExpandedOffset,
        c.getLogTag());

    if (processedText instanceof MountableCharSequence) {
      ((MountableCharSequence) processedText).onMount(textDrawable);
    }
  }

  @OnUnmount
  static void onUnmount(
      ComponentContext c,
      TextDrawable textDrawable,
      @Prop(resType = ResType.STRING) CharSequence text) {
    textDrawable.unmount();

    if (text instanceof MountableCharSequence) {
      ((MountableCharSequence) text).onUnmount(textDrawable);
    }
  }

  @OnPopulateAccessibilityNode
  static void onPopulateAccessibilityNode(
      ComponentContext c,
      View host,
      AccessibilityNodeInfoCompat node,
      @Prop(resType = ResType.STRING) CharSequence text,
      @Prop(optional = true, resType = ResType.BOOL) boolean isSingleLine) {
    if (ViewCompat.getImportantForAccessibility(host)
        == ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
      ViewCompat.setImportantForAccessibility(host, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    CharSequence contentDescription = node.getContentDescription();
    CharSequence textWithContentDescriptions = replaceContentDescriptionSpans(text);
    node.setText(contentDescription != null ? contentDescription : textWithContentDescriptions);
    node.setContentDescription(
        contentDescription != null ? contentDescription : textWithContentDescriptions);

    node.addAction(AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
    node.addAction(AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY);
    node.setMovementGranularities(
        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER
            | AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD
            | AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH);

    if (!isSingleLine) {
      node.setMultiLine(true);
    }
  }

  /**
   * Takes a text input, searches through it for any ContentDescriptionSpans, and if any are found
   * replaces the text at the spanned location with the text defined in the content description.
   * This is meant to generate text to set directly on the AccessibilityNodeInfo, not text for
   * display.
   *
   * @param text Text to modify.
   * @return The input text with the content of all ContentDescriptionSpans included.
   */
  private static CharSequence replaceContentDescriptionSpans(CharSequence text) {
    if (!(text instanceof Spanned)) {
      return text;
    }

    final Spanned spanned = (Spanned) text;
    ContentDescriptionSpan[] contentDescriptionSpans =
        spanned.getSpans(0, text.length(), ContentDescriptionSpan.class);

    if (contentDescriptionSpans.length == 0) {
      return text;
    }

    SpannableStringBuilder spannable = new SpannableStringBuilder(text);
    for (ContentDescriptionSpan span : contentDescriptionSpans) {
      CharSequence replacementText = span.getContentDescription();
      if (TextUtils.isEmpty(replacementText)) {
        continue;
      }
      int spanReplaceStart = spannable.getSpanStart(span);
      int spanReplaceEnd = spannable.getSpanEnd(span);
      spannable.replace(spanReplaceStart, spanReplaceEnd, replacementText);
    }

    return spannable.toString();
  }

  @GetExtraAccessibilityNodesCount
  static int getExtraAccessibilityNodesCount(
      ComponentContext c,
      @Prop(optional = true, resType = ResType.BOOL) boolean accessibleClickableSpans,
      @FromBoundsDefined ClickableSpan[] clickableSpans) {
    return (accessibleClickableSpans && clickableSpans != null) ? clickableSpans.length : 0;
  }

  @OnPerformActionForVirtualView
  static boolean onPerformActionForVirtualView(
      ComponentContext c,
      View host,
      AccessibilityNodeInfoCompat accessibilityNode,
      int virtualViewId,
      int action,
      @Nullable Bundle arguments,
      @FromBoundsDefined ClickableSpan[] clickableSpans) {
    if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
      clickableSpans[virtualViewId].onClick(host);
      return true;
    }
    return false;
  }

  @OnVirtualViewKeyboardFocusChanged
  static void onVirtualViewKeyboardFocusChanged(
      ComponentContext c,
      View host,
      @Nullable AccessibilityNodeInfoCompat accessibilityNode,
      int virtualViewId,
      boolean hasFocus,
      @FromBoundsDefined ClickableSpan[] clickableSpans) {
    final ClickableSpan span = clickableSpans[virtualViewId];
    if (span instanceof AccessibleClickableSpan) {
      ((AccessibleClickableSpan) span).setKeyboardFocused(hasFocus);
      // force redraw when focus changes, so that any visual changes get applied.
      host.invalidate();
    }
  }

  @OnPopulateExtraAccessibilityNode
  static void onPopulateExtraAccessibilityNode(
      ComponentContext c,
      AccessibilityNodeInfoCompat node,
      int extraNodeIndex,
      int componentBoundsLeft,
      int componentBoundsTop,
      @Prop(resType = ResType.STRING) CharSequence text,
      @FromBoundsDefined Layout textLayout,
      @FromBoundsDefined ClickableSpan[] clickableSpans) {
    if (!(text instanceof Spanned)) {
      return;
    }

    final Spanned spanned = (Spanned) text;

    final ClickableSpan span = clickableSpans[extraNodeIndex];
    final int start = spanned.getSpanStart(span);
    final int end = spanned.getSpanEnd(span);
    final int startLine = textLayout.getLineForOffset(start);
    final int endLine = textLayout.getLineForOffset(end);

    // The bounds for multi-line strings should *only* include the first line.  This is because
    // Talkback triggers its click at the center point of these bounds, and if that center point
    // is outside the spannable, it will click on something else.  There is no harm in not outlining
    // the wrapped part of the string, as the text for the whole string will be read regardless of
    // the bounding box.
    final int selectionPathEnd =
        startLine == endLine ? end : textLayout.getLineVisibleEnd(startLine);

    textLayout.getSelectionPath(start, selectionPathEnd, sTempPath);
    sTempPath.computeBounds(sTempRectF, /* unused */ true);

    sTempRect.set(
        componentBoundsLeft + (int) sTempRectF.left,
        componentBoundsTop + (int) sTempRectF.top,
        componentBoundsLeft + (int) sTempRectF.right,
        componentBoundsTop + (int) sTempRectF.bottom);

    if (sTempRect.isEmpty()) {
      // Text is not actually visible.
      // Override bounds so it doesn't crash ExploreByTouchHelper.java
      sTempRect.set(0, 0, 1, 1);
      node.setBoundsInParent(sTempRect);
      node.setContentDescription(""); // make node non-focusable
      return;
    }

    node.setBoundsInParent(sTempRect);

    node.setClickable(true);
    node.setFocusable(true);
    node.setEnabled(true);
    node.setVisibleToUser(true);
    node.setText(spanned.subSequence(start, end));
    if (span instanceof AccessibleClickableSpan) {
      AccessibleClickableSpan accessibleClickableSpan = (AccessibleClickableSpan) span;
      String contentDescription = accessibleClickableSpan.getAccessibilityDescription();
      String roleDescription = accessibleClickableSpan.getRoleDescription();
      String role = accessibleClickableSpan.getAccessibilityRole();
      if (contentDescription != null) {
        node.setContentDescription(contentDescription);
      }
      if (roleDescription != null) {
        node.setRoleDescription(roleDescription);
      }
      if (role != null) {
        node.setClassName(role);
      } else {
        node.setClassName(AccessibilityRole.BUTTON);
      }
    } else {
      node.setClassName(AccessibilityRole.BUTTON);
    }
  }

  @GetExtraAccessibilityNodeAt
  static int getExtraAccessibilityNodeAt(
      ComponentContext c,
      int x,
      int y,
      @Prop(resType = ResType.STRING) CharSequence text,
      @FromBoundsDefined Layout textLayout,
      @FromBoundsDefined ClickableSpan[] clickableSpans) {
    if (!(text instanceof Spanned)) {
      return INVALID_ID;
    }

    final Spanned spanned = (Spanned) text;

    for (int i = 0; i < clickableSpans.length; i++) {
      final ClickableSpan span = clickableSpans[i];
      final int start = spanned.getSpanStart(span);
      final int end = spanned.getSpanEnd(span);

      textLayout.getSelectionPath(start, end, sTempPath);
      sTempPath.computeBounds(sTempRectF, /* unused */ true);

      if (sTempRectF.contains(x, y)) {
        return i;
      }
    }

    return INVALID_ID;
  }

  @OnBindDynamicValue
  static void onBindTextColor(
      TextDrawable textDrawable,
      @Nullable @Prop(optional = true, dynamic = true) Integer dynamicTextColor) {
    if (dynamicTextColor != null) {
      textDrawable.setTextColor(dynamicTextColor);
    }
  }

  static TextAlignment getTextAlignment(
      @Nullable Alignment alignment, @Nullable TextAlignment textAlignment) {
    if (textAlignment != null) {
      return textAlignment;
    }
    if (alignment != null) {
      switch (alignment) {
        default:
        case ALIGN_NORMAL:
          return TEXT_START;
        case ALIGN_OPPOSITE:
          return TEXT_END;
        case ALIGN_CENTER:
          return CENTER;
      }
    }
    return TEXT_START;
  }

  private static TextDirectionHeuristicCompat getTextDirection(
      @Nullable TextDirectionHeuristicCompat textDirection, YogaDirection layoutDirection) {
    if (textDirection == null) {
      textDirection =
          layoutDirection == YogaDirection.RTL
              ? TextDirectionHeuristicsCompat.FIRSTSTRONG_RTL
              : TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR;
    }
    return textDirection;
  }

  private static Alignment getLayoutAlignment(
      TextAlignment textAlignment,
      TextDirectionHeuristicCompat textDirection,
      CharSequence text,
      YogaDirection layoutDirection) {
    final Alignment alignment;
    final boolean layoutRtl, textRtl;
    switch (textAlignment) {
      default:
      case TEXT_START:
        alignment = Alignment.ALIGN_NORMAL;
        break;
      case TEXT_END:
        alignment = Alignment.ALIGN_OPPOSITE;
        break;
      case LAYOUT_START:
        layoutRtl = (layoutDirection == YogaDirection.RTL);
        textRtl = (textDirection.isRtl(text, 0, text.length()));
        alignment = (layoutRtl == textRtl) ? Alignment.ALIGN_NORMAL : Alignment.ALIGN_OPPOSITE;
        break;
      case LAYOUT_END:
        layoutRtl = (layoutDirection == YogaDirection.RTL);
        textRtl = (textDirection.isRtl(text, 0, text.length()));
        alignment = (layoutRtl == textRtl) ? Alignment.ALIGN_OPPOSITE : Alignment.ALIGN_NORMAL;
        break;
      case LEFT:
        alignment =
            textDirection.isRtl(text, 0, text.length())
                ? Alignment.ALIGN_OPPOSITE
                : Alignment.ALIGN_NORMAL;
        break;
      case RIGHT:
        alignment =
            textDirection.isRtl(text, 0, text.length())
                ? Alignment.ALIGN_NORMAL
                : Alignment.ALIGN_OPPOSITE;
        break;
      case CENTER:
        alignment = Alignment.ALIGN_CENTER;
        break;
    }
    return alignment;
  }
}
