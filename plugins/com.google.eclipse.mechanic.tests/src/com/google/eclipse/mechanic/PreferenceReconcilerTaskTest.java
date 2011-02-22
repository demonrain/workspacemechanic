/*******************************************************************************
 * Copyright (C) 2007, Google Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package com.google.eclipse.mechanic;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;

import com.google.eclipse.mechanic.PreferenceReconcilerTask.CompositeReconciler;
import com.google.eclipse.mechanic.PreferenceReconcilerTask.ContainsMatcher;
import com.google.eclipse.mechanic.PreferenceReconcilerTask.EqualsMatcher;
import com.google.eclipse.mechanic.PreferenceReconcilerTask.ImmutablePreference;
import com.google.eclipse.mechanic.PreferenceReconcilerTask.ListAppendResolver;
import com.google.eclipse.mechanic.PreferenceReconcilerTask.Matcher;
import com.google.eclipse.mechanic.PreferenceReconcilerTask.PathAppendResolver;
import com.google.eclipse.mechanic.PreferenceReconcilerTask.Preference;
import com.google.eclipse.mechanic.PreferenceReconcilerTask.Reconciler;
import com.google.eclipse.mechanic.PreferenceReconcilerTask.Resolver;
import com.google.eclipse.mechanic.PreferenceReconcilerTask.SimpleResolver;
import com.google.eclipse.mechanic.PreferenceReconcilerTask.StringReplaceResolver;
import com.google.eclipse.mechanic.tests.internal.RunAsJUnitTest;

import junit.framework.TestCase;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;

/**
 * Tests for PreferenceReconcilerTask.
 *
 * @author smckay@google.com (Steve McKay)
 */
@RunAsJUnitTest
public class PreferenceReconcilerTaskTest extends TestCase {

  private static final Preference WEE = new ImmutablePreference(
      "/instance/foo", "bar", "wee");

  private static final Preference HAA = new ImmutablePreference(
      "/instance/foo", "bar", "haa");

  public void testEqualsMatcher() {
    Matcher m = new EqualsMatcher(HAA);
    assertTrue(m.matches("haa"));
    assertFalse(m.matches("foo:bar:wee:haa"));
    assertFalse(m.matches("wee"));
  }

  public void testContainsMatcher() {
    Matcher m = new ContainsMatcher("haa");
    assertTrue(m.matches("haa"));
    assertTrue(m.matches("foo:bar:wee:haa"));
    assertFalse(m.matches("wee:sna"));
  }

  public void testSimpleResolver() {
    Resolver r = new SimpleResolver(HAA);
    assertEquals("haa", r.resolve("wee"));
  }

  public void testListAppendResolver() {
    Resolver r = new ListAppendResolver(",", HAA);
    assertEquals("wee,haa", r.resolve("wee"));
  }

  public void testListAppendResolverNull() {
    Resolver r = new ListAppendResolver(",", HAA);
    assertEquals("haa", r.resolve(null));
  }

  public void testPathAppendResolver() {
    Resolver r = new PathAppendResolver(HAA);
    assertEquals("wee:haa", r.resolve("wee"));
  }

  public void testStringReplaceResolver() {
    String test = "grrammygrr";
    assertEquals("Fizzyammygrr",
        new StringReplaceResolver("^grr", "Fizzy").resolve(test));
    assertEquals("grrammyFizzy",
        new StringReplaceResolver("grr$", "Fizzy").resolve(test));
    assertEquals("FizzyammyFizzy",
        new StringReplaceResolver("grr", "Fizzy").resolve(test));
  }

  public void testCompositeReconcilerDoesNothing() {

    IEclipsePreferences root = createMock(IEclipsePreferences.class);
    IEclipsePreferences wee = createMock(IEclipsePreferences.class);

    expect(root.node(WEE.getPath())).andReturn(wee);

    replay(root);

    // should return the correct value
    expect(wee.get(WEE.getKey(), null)).andReturn(WEE.getValue());

    replay(wee);

    // doesn't need reconciliation
    Reconciler reconciler = new CompositeReconciler(root, WEE,
        new EqualsMatcher(WEE), new SimpleResolver(WEE));

    assertTrue(reconciler.isReconciled());
  }

  public void testCompositeReconcilerReconciles() throws Exception {

    IEclipsePreferences root = createMock(IEclipsePreferences.class);
    IEclipsePreferences wee = createMock(IEclipsePreferences.class);

    expect(root.node(WEE.getPath()))
        .andReturn(wee)
        .times(1); // should be called once

    replay(root);

    // should return an incorrect value
    expect(wee.get(WEE.getKey(), null))
        .andReturn(HAA.getValue())
        .anyTimes(); // can be called as many times as we want

    wee.flush();
    expectLastCall().anyTimes();

    // It's a weird idiom, but we have to call this method before the
    // replay so that easy-mock will be aware of the fact that we will
    // be calling it after the replay. Else we get an exception.
    wee.put(WEE.getKey(), WEE.getValue());

    replay(wee);

    // needs reconciliation
    Reconciler reconciler = new CompositeReconciler(root, HAA,
        new EqualsMatcher(WEE), new SimpleResolver(WEE));

    assertFalse(reconciler.isReconciled());
    reconciler.reconcile(); // causes wee.put to be called.
  }

  public void testParseStringPreferenceNullAndEmptyArg() {

    try {
      PreferenceReconcilerTask.parsePreferenceString(null);
      fail("Should have thrown NullPointerException.");
    } catch (NullPointerException expected) {
      // as expected
    }

    try {
      PreferenceReconcilerTask.parsePreferenceString("");
      fail("Should have thrown IllegalArgumentException.");
    } catch (IllegalArgumentException expected) {
      // as expected
    }
  }

  public void testParseStringPreferenceMalformedArg() {

    try {
      PreferenceReconcilerTask.parsePreferenceString(
          "/asdf/asdf/");
      fail("Should have thrown IllegalArgumentException.");
    } catch (IllegalArgumentException expected) {
      // as expected
    }

    try {
      PreferenceReconcilerTask.parsePreferenceString(
          "/asdf/asdf/=");
      fail("Should have thrown IllegalArgumentException.");
    } catch (IllegalArgumentException expected) {
      // as expected
    }
  }

  public void testParseStringPreferenceEmptyStringValue() {

    Preference pref = PreferenceReconcilerTask.parsePreferenceString(
        "/asdf/asdf=");
    assertEquals("", pref.getValue());

  }

  public void testParseStringPreferenceGoodFields() {

    Preference pref = PreferenceReconcilerTask.parsePreferenceString(
        "/instance/org.eclipse.core.resources/version=1");
    assertEquals("/instance/org.eclipse.core.resources", pref.getPath());
    assertEquals("version", pref.getKey());
    assertEquals("1", pref.getValue());
  }

  public void testParseStringPreferenceShortFields() {
    // try it with short value to be sure our length checks aren't wrong.
    Preference pref = PreferenceReconcilerTask.parsePreferenceString(
        "/a/b=c");
    assertEquals("/a", pref.getPath());
    assertEquals("b", pref.getKey());
    assertEquals("c", pref.getValue());
  }
}
