/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.content.testsuite;

import java.time.LocalDateTime;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.common.collect.NestedAttributesMap;
import org.sonatype.nexus.common.entity.Continuation;
import org.sonatype.nexus.content.raw.internal.store.RawAssetDAO;
import org.sonatype.nexus.content.raw.internal.store.RawComponentDAO;
import org.sonatype.nexus.content.testsupport.raw.RawITSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.content.Component;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.content.store.AssetData;
import org.sonatype.nexus.repository.content.store.AssetStore;
import org.sonatype.nexus.repository.content.store.ComponentData;
import org.sonatype.nexus.repository.content.store.ComponentStore;
import org.sonatype.nexus.repository.content.store.FormatStoreManager;
import org.sonatype.nexus.repository.purge.PurgeUnusedFacet;
import org.sonatype.nexus.repository.raw.internal.RawFormat;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.junit.Assert.assertThat;
import static org.sonatype.nexus.content.matcher.AssetMatcher.path;
import static org.sonatype.nexus.content.matcher.ComponentMatcher.name;
import static org.sonatype.nexus.datastore.api.DataStoreManager.CONTENT_DATASTORE_NAME;
import static org.sonatype.nexus.repository.config.ConfigurationConstants.DATA_STORE_NAME;
import static org.sonatype.nexus.repository.config.ConfigurationConstants.STORAGE;

public class PurgeUnusedIT
    extends RawITSupport
{
  private static final String COMPONENT_KIND = "";

  private final String PROXY_REPOSITORY_NAME = "raw-proxy-" + testName.getMethodName();

  private static final String ASSET_KIND = "";

  private static final String NAMESPACE = "namespace";

  private static final String VERSION = "version";

  private static final int BROWSE_LIMIT = 8;

  private Repository repository;

  private ContentFacet contentFacet;

  private AssetStore<RawAssetDAO> assetStore;

  private ComponentStore<RawComponentDAO> componentStore;

  @Inject
  @Named(RawFormat.NAME)
  private FormatStoreManager formatStoreManager;

  @Before
  public void setUp() {
    repository = repos.createRawProxy(PROXY_REPOSITORY_NAME, "http://example.net");
    contentFacet = repository.facet(ContentFacet.class);
    NestedAttributesMap storageAttributes = repository.getConfiguration().attributes(STORAGE);
    String contentStoreName = storageAttributes.get(DATA_STORE_NAME, String.class, CONTENT_DATASTORE_NAME);
    assetStore = formatStoreManager.assetStore(contentStoreName);
    componentStore = formatStoreManager.componentStore(contentStoreName);
  }

  @Test
  public void shouldPurgeUnusedAssets() {
    assertThat(browseAssets(), iterableWithSize(0));
    createAssetWithLastDownloadedDate("3days", LocalDateTime.now().minusDays(3));
    createAssetWithLastDownloadedDate("2days", LocalDateTime.now().minusDays(2));
    createAssetWithLastDownloadedDate("1day", LocalDateTime.now().minusDays(1));
    createAssetWithLastDownloadedDate("0day", LocalDateTime.now());
    assertThat(browseAssets(), iterableWithSize(4));

    repository.facet(PurgeUnusedFacet.class).purgeUnused(2);

    Continuation<FluentAsset> assets = browseAssets();
    assertThat(assets, iterableWithSize(2));
    assertThat(assets,
        hasItems(
            path(equalTo("0day")),
            path(equalTo("1day"))
        ));
  }

  @Test
  public void shouldPurgeUnusedComponents() {
    assertThat(browseComponents(), iterableWithSize(0));
    assertThat(browseAssets(), iterableWithSize(0));
    createComponentWithAssetLastDownloadedDate("3days", LocalDateTime.now().minusDays(3));
    createComponentWithAssetLastDownloadedDate("2days", LocalDateTime.now().minusDays(2));
    createComponentWithAssetLastDownloadedDate("1day", LocalDateTime.now().minusDays(1));
    createComponentWithAssetLastDownloadedDate("0day", LocalDateTime.now());
    assertThat(browseComponents(), iterableWithSize(4));
    assertThat(browseAssets(), iterableWithSize(4));

    repository.facet(PurgeUnusedFacet.class).purgeUnused(2);

    Continuation<FluentComponent> components = browseComponents();
    assertThat(components, iterableWithSize(2));
    assertThat(components,
        hasItems(
            name(equalTo("0day")),
            name(equalTo("1day"))
        ));

    Continuation<FluentAsset> assets = browseAssets();
    assertThat(assets, iterableWithSize(2));
    assertThat(assets,
        hasItems(
            path(equalTo("0day0")),
            path(equalTo("1day0"))
        ));
  }

  @Test
  public void shouldPurgeComponentsBasedOnMostRecentlyDownloadedAsset() {
    assertThat(browseComponents(), iterableWithSize(0));
    assertThat(browseAssets(), iterableWithSize(0));
    LocalDateTime now = LocalDateTime.now();
    createComponentWithAssetLastDownloadedDate("0day-3days", now, now.minusDays(3));
    createComponentWithAssetLastDownloadedDate("2day-3days", now.minusDays(2), now.minusDays(2));
    assertThat(browseComponents(), iterableWithSize(2));
    assertThat(browseAssets(), iterableWithSize(4));

    repository.facet(PurgeUnusedFacet.class).purgeUnused(2);

    Continuation<FluentComponent> components = browseComponents();
    assertThat(components, iterableWithSize(1));
    assertThat(components, hasItems(name(equalTo("0day-3days"))));

    Continuation<FluentAsset> assets = browseAssets();
    assertThat(assets, iterableWithSize(2));
    assertThat(assets,
        hasItems(
            path(equalTo("0day-3days0")),
            path(equalTo("0day-3days1"))
        ));
  }

  private Continuation<FluentAsset> browseAssets() {
    return contentFacet.assets().browse(BROWSE_LIMIT, null);
  }

  private Continuation<FluentComponent> browseComponents() {
    return contentFacet.components().browse(BROWSE_LIMIT, null);
  }

  private void createAssetWithLastDownloadedDate(final String path, final LocalDateTime lastDownloaded) {
    assetStore.createAsset(prepareAssetLastDownloaded(path, lastDownloaded));
  }

  private AssetData prepareAssetLastDownloaded(final String path, final LocalDateTime lastDownloaded) {
    AssetData assetData = new AssetData();
    assetData.setRepositoryId(contentFacet.contentRepositoryId());
    assetData.setPath(path);
    assetData.setLastDownloaded(lastDownloaded);
    assetData.setKind(ASSET_KIND);
    return assetData;
  }

  private void createComponentWithAssetLastDownloadedDate(final String name, final LocalDateTime... lastDownloaded) {
    ComponentData componentData = new ComponentData();
    componentData.setRepositoryId(contentFacet.contentRepositoryId());
    componentData.setNamespace(NAMESPACE);
    componentData.setName(name);
    componentData.setKind(COMPONENT_KIND);
    componentData.setVersion(VERSION);
    componentStore.createComponent(componentData);

    Optional<Component> maybeComponent =
        componentStore.readComponent(contentFacet.contentRepositoryId(), NAMESPACE, name, VERSION);
    assertThat(maybeComponent.isPresent(), is(true));
    Component component = maybeComponent.get();

    int counter = 0;
    for (LocalDateTime lastDownloadedAsset : lastDownloaded) {
      AssetData assetData = prepareAssetLastDownloaded(name + counter++, lastDownloadedAsset);
      assetData.setComponent(component);
      assetStore.createAsset(assetData);
    }
  }
}
