package app.todo

import android.app.Application
import app.todo.data.TodoDatabase

class TodoApp : Application() {
    val db: TodoDatabase by lazy { TodoDatabase.create(this) }
}
