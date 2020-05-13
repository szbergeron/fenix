/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.ui

import androidx.test.espresso.NoMatchingViewException
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mozilla.fenix.helpers.AndroidAssetDispatcher
import org.mozilla.fenix.helpers.HomeActivityTestRule
import org.mozilla.fenix.helpers.TestAssetHelper
import org.mozilla.fenix.ui.robots.homeScreen
import org.mozilla.fenix.ui.robots.navigationToolbar


/**
 *  Tests for verifying basic functionality of tab collection
 *
 */

class CollectionTest {
    /* ktlint-disable no-blank-line-before-rbrace */ // This imposes unreadable grouping.

    private val mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var mockWebServer: MockWebServer
    private val defaultCollectionName = "Collection 1"
    private val newCollectionName = "testcollection"

    @get:Rule
    val activityTestRule = HomeActivityTestRule()

    @Before
    fun setUp() {
        mockWebServer = MockWebServer().apply {
            setDispatcher(AndroidAssetDispatcher())
            start()
        }
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun verifyCreateFirstCollectionFlowItems() {
        val firstWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)
        val secondWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 2)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(firstWebPage.url) {
        }.openHomeScreen {
        }.openNavigationToolbar {
        }.enterURLAndEnterToBrowser(secondWebPage.url) {
        }.openHomeScreen {
            clickSaveCollectionButton()
            verifySelectTabsView()
            selectAllTabsForCollection()
            verifyTabsSelectedCounterText(2)
            deselectAllTabsForCollection()
            verifyTabsSelectedCounterText(0)
            selectTabForCollection(firstWebPage.title)
            verifyTabsSelectedCounterText(1)
            selectAllTabsForCollection()
            saveTabsSelectedForCollection()
            verifyNameCollectionView()
            verifyDefaultCollectionName("Collection 1")
            typeCollectionName(newCollectionName)
            verifySnackBarText("Tabs saved!")
            verifyExistingOpenTabs(firstWebPage.title)
            verifyExistingOpenTabs(secondWebPage.title)
            verifyCollectionIsDisplayed(newCollectionName)
        }
    }

    @Test
    // open a webpage, and add currently to an existing collection
    fun addTabToExistingCollectionTest() {
        val firstWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)
        val secondWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 2)

        createCollection1()

        homeScreen {
            verifyExistingTabList()
            closeTab()
        }.openNavigationToolbar {
        }.enterURLAndEnterToBrowser(secondWebPage.url) {
            waitForPageLoad()
        }.openThreeDotMenu {
            clickBrowserViewSaveCollectionButton()
        }.selectExistingCollection(defaultCollectionName) {
            verifySnackBarText("Tab saved!")
        }.openHomeScreen {
            verifyExistingTabList()
            expandCollection(defaultCollectionName)
            verifyItemInCollectionExists(firstWebPage.title)
            verifyItemInCollectionExists(secondWebPage.title)
        }
    }

    @Test
    fun collectionMenuAddTabButtonTest() {
        val secondWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 2)

        createCollection1()

        homeScreen {
            closeTab()
        }.openNavigationToolbar {
        }.enterURLAndEnterToBrowser(secondWebPage.url) {
        }.openHomeScreen {
            expandCollection(defaultCollectionName)
            clickCollectionThreeDotButton()
            selectAddTabToCollection()
            verifyTabsSelectedCounterText(1)
            saveTabsSelectedForCollection()
            verifySnackBarText("Tab saved!")
            verifyItemInCollectionExists(secondWebPage.title)
        }
    }

    @Test
    fun collectionMenuOpenAllTabsTest() {
        val firstWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        createCollection1()

        homeScreen {
            closeTab()
            expandCollection(defaultCollectionName)
            clickCollectionThreeDotButton()
            selectOpenTabs()
            verifyExistingOpenTabs(firstWebPage.title)
        }
    }

    @Test
    fun renameCollectionTest() {
        createCollection1()

        homeScreen {
            // On homeview, tap the 3-dot button to expand, select rename, rename collection
            expandCollection(defaultCollectionName)
            clickCollectionThreeDotButton()
            selectRenameCollection()
            typeCollectionName("renamed_collection")
            verifyCollectionIsDisplayed("renamed_collection")
        }
    }

    @Test
    fun deleteCollectionTest() {
        createCollection1()

        homeScreen {
            expandCollection(defaultCollectionName)
            clickCollectionThreeDotButton()
            selectDeleteCollection()
            confirmDeleteCollection()
            verifyNoCollectionsHeader()
        }
    }

    @Test
    fun createCollectionFromTabTest() {
        val firstWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        createCollection1()
        homeScreen {
            // swipe to bottom until the collections are shown
            verifyExistingOpenTabs(firstWebPage.title)
            try {
                verifyCollectionIsDisplayed(defaultCollectionName)
            } catch (e: NoMatchingViewException) {
                scrollToElementByText(defaultCollectionName)
            }
        }
    }

    @Test
    fun verifyExpandedCollectionItemsTest() {
        val firstWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        createCollection1()

        homeScreen {
            verifyCollectionIsDisplayed(defaultCollectionName)
            verifyCollectionIcon()
            expandCollection(defaultCollectionName)
            verifyItemInCollectionExists(firstWebPage.title)
            verifyCollectionItemLogo()
            verifyCollectionItemUrl()
            verifyShareCollectionButtonIsVisible(true)
            verifyCollectionMenuIsVisible(true)
            verifyCollectionItemRemoveButtonIsVisible(firstWebPage.title, true)
            collapseCollection(defaultCollectionName)
            verifyItemInCollectionExists(firstWebPage.title, false)
            verifyShareCollectionButtonIsVisible(false)
            verifyCollectionMenuIsVisible(false)
            verifyCollectionItemRemoveButtonIsVisible(firstWebPage.title, false)
        }
    }

    @Test
    fun shareCollectionTest() {
        createCollection1()
        homeScreen {
            expandCollection(defaultCollectionName)
            clickShareCollectionButton()
            verifyShareTabsOverlay()
        }
    }

    @Test
    fun removeTabFromCollectionTest() {
        val webPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        createCollection1()
        homeScreen {
            closeTab()
            expandCollection(defaultCollectionName)
            removeTabFromCollection(webPage.title)
            verifyItemInCollectionExists(webPage.title, false)
        }

        createCollection1()
        homeScreen {
            closeTab()
            expandCollection(defaultCollectionName)
            swipeCollectionItemLeft(webPage.title)
            verifyItemInCollectionExists(webPage.title, false)
        }

        createCollection1()
        homeScreen {
            closeTab()
            expandCollection(defaultCollectionName)
            swipeCollectionItemRight(webPage.title)
            verifyItemInCollectionExists(webPage.title, false)
        }
    }

    @Test
    fun selectTabOnLongTapTest() {
        val firstWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)
        val secondWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 2)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(firstWebPage.url) {
        }.openHomeScreen {
        }.openNavigationToolbar {
        }.enterURLAndEnterToBrowser(secondWebPage.url) {
        }.openHomeScreen {
            longTapSelectTab(firstWebPage.title)
            verifySelectTabsView()
            verifyTabsSelectedCounterText(1)
            selectTabForCollection(secondWebPage.title)
            verifyTabsSelectedCounterText(2)
            saveTabsSelectedForCollection()
            submitDefaultCollectionName()
            verifySnackBarText("Tabs saved!")
            closeTabViaXButton(firstWebPage.title)
            closeTabViaXButton(secondWebPage.title)
            expandCollection(defaultCollectionName)
            verifyItemInCollectionExists(firstWebPage.title)
            verifyItemInCollectionExists(secondWebPage.title)
        }
    }

    @Test
    fun tabsOverflowMenuSaveCollectionTest() {
        val firstWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)
        val secondWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 2)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(firstWebPage.url) {
        }.openHomeScreen {
        }.openNavigationToolbar {
        }.enterURLAndEnterToBrowser(secondWebPage.url) {
        }.openHomeScreen {
        }.openTabsListThreeDotMenu {
            verifySaveCollection()
        }.clickOpenTabsMenuSaveCollection {
            verifySelectTabsView()
            verifyTabsSelectedCounterText(0)
            selectAllTabsForCollection()
            verifyTabsSelectedCounterText(2)
            saveTabsSelectedForCollection()
            submitDefaultCollectionName()
            closeTabViaXButton(firstWebPage.title)
            closeTabViaXButton(secondWebPage.title)
            expandCollection(defaultCollectionName)
            verifyItemInCollectionExists(firstWebPage.title)
            verifyItemInCollectionExists(secondWebPage.title)
        }
    }

    @Test
    fun navigateBackInCollectionFlowTest() {
        val secondWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 2)

        createCollection1()
        navigationToolbar {
        }.enterURLAndEnterToBrowser(secondWebPage.url) {
        }.openHomeScreen {
            longTapSelectTab(secondWebPage.title)
            verifySelectTabsView()
            saveTabsSelectedForCollection()
            verifySelectCollectionView()
            clickAddNewCollection()
            verifyNameCollectionView()
            goBackCollectionFlow()
            verifySelectCollectionView()
            goBackCollectionFlow()
            verifySelectTabsView()
            goBackCollectionFlow()
            verifyHomeComponent()
        }
    }

//    private fun createCollection(name: String, firstCollection: Boolean = true) {
//        val firstWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)
//
//        navigationToolbar {
//        }.enterURLAndEnterToBrowser(firstWebPage.url) {
//            waitForPageLoad()
//        }.openHomeScreen {
//            clickSaveCollectionButton()
//            if (!firstCollection)
//                clickAddNewCollection()
//            typeCollectionName(newCollectionName)
//
//            mDevice.wait(
//                Until.findObject(By.text(name)),
//                TestAssetHelper.waitingTime
//            )
//        }
//    }

    private fun createCollection1() {
        val firstWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(firstWebPage.url) {
            waitForPageLoad()
        }.openHomeScreen {
            clickSaveCollectionButton()
            submitDefaultCollectionName()
            getInstrumentation().waitForIdleSync()
            verifyCollectionIsDisplayed(defaultCollectionName)
        }
    }
}
