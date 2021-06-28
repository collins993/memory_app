package io.github.collins993.memoryapp.models

import com.google.firebase.firestore.PropertyName

data class UserImageList(
        @PropertyName("images")val images: List<String>? = null
)