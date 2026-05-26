package org.thoughtcrime.securesms.stories.saved

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.stories.cloudstorage.SavedStoryRecord

class SavedStoriesViewModel(private val repository: SavedStoriesRepository) : ViewModel() {

  private val _stories = MutableLiveData<List<SavedStoryRecord>>()
  val stories: LiveData<List<SavedStoryRecord>> = _stories

  init {
    refresh()
  }

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) {
      val records = repository.getSavedStories()
      _stories.postValue(records)
    }
  }

  class Factory(private val repository: SavedStoriesRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(SavedStoriesViewModel(repository)) as T
    }
  }
}
