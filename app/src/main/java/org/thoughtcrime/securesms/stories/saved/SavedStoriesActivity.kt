package org.thoughtcrime.securesms.stories.saved

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme

class SavedStoriesActivity : PassphraseRequiredActivity() {

  private val dynamicTheme = DynamicNoActionBarTheme()

  companion object {
    @JvmStatic
    fun intent(context: Context): Intent {
      return Intent(context, SavedStoriesActivity::class.java)
    }
  }

  override fun onPreCreate() {
    dynamicTheme.onCreate(this)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    setContentView(R.layout.saved_stories_activity)

    val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)

    if (savedInstanceState == null) {
      supportFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, SavedStoriesFragment())
        .commit()
    }
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
      return true
    }
    return super.onOptionsItemSelected(item)
  }
}
