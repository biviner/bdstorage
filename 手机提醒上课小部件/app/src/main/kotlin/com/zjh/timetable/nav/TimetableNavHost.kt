package com.zjh.timetable.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.zjh.timetable.ui.confirm.ConfirmScreen
import com.zjh.timetable.ui.edit.CourseEditScreen
import com.zjh.timetable.ui.home.HomeScreen
import com.zjh.timetable.ui.import.ImportScreen
import com.zjh.timetable.ui.settings.SettingsScreen
import com.zjh.timetable.ui.week.WeekTimetableScreen

/**
 * 导航骨架（详细设计 §8.1）：home / week/{weekNo} / courseEdit?courseId= / settings / import / confirm/{draftId}。
 */
object Routes {
    const val HOME = "home"
    const val WEEK = "week/{weekNo}"
    const val COURSE_EDIT = "courseEdit?courseId={courseId}"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
    const val CONFIRM = "confirm/{draftId}"

    fun week(weekNo: Int) = "week/$weekNo"

    /** courseId = -1 表示新建（CourseEditViewModel.NEW_COURSE_ID）。 */
    fun courseEdit(courseId: Long = -1L) = "courseEdit?courseId=$courseId"

    fun confirm(draftId: Long) = "confirm/$draftId"
}

@Composable
fun TimetableNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenWeek = { week -> navController.navigate(Routes.week(week)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onEditCourse = { id -> navController.navigate(Routes.courseEdit(id)) },
                onAddCourse = { navController.navigate(Routes.courseEdit()) },
                onImport = { navController.navigate(Routes.IMPORT) },
            )
        }

        composable(
            Routes.WEEK,
            arguments = listOf(navArgument("weekNo") { type = NavType.IntType; defaultValue = 1 }),
        ) { entry ->
            val weekNo = entry.arguments?.getInt("weekNo") ?: 1
            WeekTimetableScreen(
                initialWeek = weekNo,
                onBack = { navController.popBackStack() },
                onEditCourse = { id -> navController.navigate(Routes.courseEdit(id)) },
                onAddCourse = { navController.navigate(Routes.courseEdit()) },
            )
        }

        composable(
            Routes.COURSE_EDIT,
            // B4-2：2×2 小组件点击深链直达课程详情（§7.1）
            deepLinks = listOf(navDeepLink { uriPattern = "timetable://courseEdit?courseId={courseId}" }),
            arguments = listOf(navArgument("courseId") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            CourseEditScreen(
                courseId = entry.arguments?.getLong("courseId") ?: -1L,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.IMPORT) {
            ImportScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onRecognized = { draftId -> navController.navigate(Routes.confirm(draftId)) },
            )
        }

        composable(
            Routes.CONFIRM,
            arguments = listOf(navArgument("draftId") { type = NavType.LongType }),
        ) { entry ->
            val draftId = entry.arguments?.getLong("draftId") ?: -1L
            ConfirmScreen(
                draftId = draftId,
                onBack = { navController.popBackStack() },
                // 导入成功 → 跳周课表，清掉 import/confirm 两级，返回落在首页
                onImported = { week ->
                    navController.navigate(Routes.week(week)) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }
    }
}

