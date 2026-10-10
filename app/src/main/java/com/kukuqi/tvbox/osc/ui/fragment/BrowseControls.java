package com.kukuqi.tvbox.osc.ui.fragment;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.kukuqi.tvbox.osc.R;
import com.kukuqi.tvbox.osc.ui.dialog.VideoLinkDialog;
import java.util.function.Supplier;

final class BrowseControls {
    static void attach(Fragment fragment, SwipeRefreshLayout swipe, FloatingActionButton button,
                       RecyclerView list, Supplier<RecyclerView> current, Runnable refresh, Runnable filter) {
        Runnable update = () -> {
            boolean top = !current.get().canScrollVertically(-1);
            button.setImageResource(top ? (filter == null ? R.drawable.ic_live_tv_48 : R.drawable.ic_filter_list) : R.drawable.ic_scroll_top);
            button.setContentDescription(top ? (filter == null ? "链接播放" : "筛选") : "回到顶部");
        };
        swipe.setOnChildScrollUpCallback((parent, child) -> current.get().canScrollVertically(-1));
        swipe.setOnRefreshListener(refresh::run);
        button.setOnClickListener(v -> {
            RecyclerView active = current.get();
            if (active.canScrollVertically(-1)) { active.scrollToPosition(0); active.post(update); }
            else if (filter != null) filter.run();
            else VideoLinkDialog.show(fragment);
        });
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView recycler, int dx, int dy) { if (current.get() == recycler) update.run(); }
        });
        list.post(update);
    }
}
