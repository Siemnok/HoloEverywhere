
package org.holoeverywhere.widget;

import org.holoeverywhere.IHoloActivity.OnWindowFocusChangeListener;
import org.holoeverywhere.app.Activity;
import org.holoeverywhere.app.Application;
import org.holoeverywhere.util.LongSparseArray;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.DataSetObserver;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.AttributeSet;
import android.util.SparseBooleanArray;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Checkable;
import android.widget.ListAdapter;

import com.actionbarsherlock.internal.view.menu.ContextMenuBuilder.ContextMenuInfoGetter;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class ListView extends android.widget.ListView implements OnWindowFocusChangeListener,
        ContextMenuInfoGetter {
    public interface MultiChoiceModeListener extends ActionMode.Callback {
        public void onItemCheckedStateChanged(ActionMode mode, int position,
                long id, boolean checked);
    }

    private class MultiChoiceModeWrapper implements MultiChoiceModeListener {
        private MultiChoiceModeListener mWrapped;

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return mWrapped.onActionItemClicked(mode, item);
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            if (mWrapped.onCreateActionMode(mode, menu)) {
                setLongClickable(false);
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mWrapped.onDestroyActionMode(mode);
            mChoiceActionMode = null;
            clearChoices();
            invalidateViews();
            setLongClickable(true);
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode,
                int position, long id, boolean checked) {
            mWrapped.onItemCheckedStateChanged(mode, position, id, checked);
            if (getCheckedItemCount() == 0) {
                mode.finish();
            }
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return mWrapped.onPrepareActionMode(mode, menu);
        }

        public void setWrapped(MultiChoiceModeListener wrapped) {
            mWrapped = wrapped;
        }
    }

    private final class OnItemLongClickListenerWrapper implements OnItemLongClickListener {
        private OnItemLongClickListener wrapped;

        @Override
        public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
            return performItemLongClick(view, position, id);
        }

        public void setWrapped(OnItemLongClickListener wrapped) {
            this.wrapped = wrapped;
            if (wrapped != null) {
                setLongClickable(true);
            }
        }
    }

    public static final int CHOICE_MODE_MULTIPLE_MODAL = AbsListView.CHOICE_MODE_MULTIPLE_MODAL;
    private Activity mActivity;
    private ListAdapterWrapper mAdapter;
    private boolean mAdapterHasStableIds;
    private LongSparseArray<Integer> mCheckedIdStates;
    private int mCheckedItemCount;
    private SparseBooleanArray mCheckStates;
    private ActionMode mChoiceActionMode;
    private int mChoiceMode;
    private ContextMenuInfo mContextMenuInfo;
    private MultiChoiceModeWrapper mMultiChoiceModeCallback;
    private final OnItemLongClickListenerWrapper mOnItemLongClickListenerWrapper;
    private boolean mEnableModalBackgroundWrapper;

    public void setEnableModalBackgroundWrapper(boolean enableModalBackgroundWrapper) {
        if (enableModalBackgroundWrapper == this.mEnableModalBackgroundWrapper) {
            return;
        }
        this.mEnableModalBackgroundWrapper = enableModalBackgroundWrapper;
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    public ListView(Context context) {
        this(context, null);
    }

    public ListView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.listViewStyle);
    }

    @SuppressLint("NewApi")
    public ListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (context instanceof Activity) {
            setActivity((Activity) context);
        }
        if (Application.config().isDisableOverscrollEffects() && VERSION.SDK_INT >= 9) {
            setOverScrollMode(OVER_SCROLL_NEVER);
        }
        mOnItemLongClickListenerWrapper = new OnItemLongClickListenerWrapper();
        super.setOnItemLongClickListener(mOnItemLongClickListenerWrapper);
        setLongClickable(false);
    }

    @Override
    public void clearChoices() {
        if (mCheckStates != null) {
            mCheckStates.clear();
        }
        if (mCheckedIdStates != null) {
            mCheckedIdStates.clear();
        }
        mCheckedItemCount = 0;
    }

    protected ContextMenuInfo createContextMenuInfo(View view, int position,
            long id) {
        return new AdapterContextMenuInfo(view, position, id);
    }

    public Activity getActivity() {
        return mActivity;
    }

    @Override
    public int getCheckedItemCount() {
        return mCheckedItemCount;
    }

    @Override
    public long[] getCheckedItemIds() {
        if (mChoiceMode == CHOICE_MODE_NONE || mCheckedIdStates == null || mAdapter == null) {
            return new long[0];
        }
        final LongSparseArray<Integer> idStates = mCheckedIdStates;
        final int count = idStates.size();
        final long[] ids = new long[count];
        for (int i = 0; i < count; i++) {
            ids[i] = idStates.keyAt(i);
        }
        return ids;
    }

    @Override
    public int getCheckedItemPosition() {
        if (mChoiceMode == CHOICE_MODE_SINGLE && mCheckStates != null && mCheckStates.size() == 1) {
            return mCheckStates.keyAt(0);
        }
        return INVALID_POSITION;
    }

    @Override
    public SparseBooleanArray getCheckedItemPositions() {
        if (mChoiceMode != CHOICE_MODE_NONE) {
            return mCheckStates;
        }
        return null;
    }

    @Override
    @Deprecated
    public long[] getCheckItemIds() {
        return getCheckedItemIds();
    }

    @Override
    public int getChoiceMode() {
        return mChoiceMode;
    }

    @Override
    public ContextMenuInfo getContextMenuInfo() {
        return mContextMenuInfo;
    }

    @Override
    public boolean isItemChecked(int position) {
        if (mChoiceMode != CHOICE_MODE_NONE && mCheckStates != null) {
            return mCheckStates.get(position);
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!isEnabled()) {
                    return true;
                }
                if (isClickable() && isPressed() &&
                        getSelectedItemPosition() >= 0 && mAdapter != null &&
                        getSelectedItemPosition() < mAdapter.getCount()) {
                    final View view = getChildAt(getSelectedItemPosition()
                            - getFirstVisiblePosition());
                    if (view != null) {
                        performItemClick(view, getSelectedItemPosition(), getSelectedItemId());
                        view.setPressed(false);
                    }
                    setPressed(false);
                    return true;
                }
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) {
            invalidate();
            invalidateViews();
        }
    }

    @Override
    public boolean performItemClick(View view, int position, long id) {
        boolean handled = false;
        boolean dispatchItemClick = true;
        if (mChoiceMode != CHOICE_MODE_NONE) {
            handled = true;
            boolean checkedStateChanged = false;
            if (mChoiceMode == CHOICE_MODE_MULTIPLE ||
                    mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL && mChoiceActionMode != null) {
                boolean newValue = !mCheckStates.get(position, false);
                mCheckStates.put(position, newValue);
                if (mCheckedIdStates != null && mAdapter.hasStableIds()) {
                    if (newValue) {
                        mCheckedIdStates.put(mAdapter.getItemId(position), position);
                    } else {
                        mCheckedIdStates.delete(mAdapter.getItemId(position));
                    }
                }
                if (newValue) {
                    mCheckedItemCount++;
                } else {
                    mCheckedItemCount--;
                }
                if (mChoiceActionMode != null) {
                    mMultiChoiceModeCallback.onItemCheckedStateChanged(mChoiceActionMode,
                            position, id, newValue);
                    dispatchItemClick = false;
                }
                checkedStateChanged = true;
            } else if (mChoiceMode == CHOICE_MODE_SINGLE) {
                boolean newValue = !mCheckStates.get(position, false);
                if (newValue) {
                    mCheckStates.clear();
                    mCheckStates.put(position, true);
                    if (mCheckedIdStates != null && mAdapter.hasStableIds()) {
                        mCheckedIdStates.clear();
                        mCheckedIdStates.put(mAdapter.getItemId(position), position);
                    }
                    mCheckedItemCount = 1;
                } else if (mCheckStates.size() == 0 || !mCheckStates.valueAt(0)) {
                    mCheckedItemCount = 0;
                }
                checkedStateChanged = true;
            }
            if (checkedStateChanged) {
                updateOnScreenCheckedViews();
            }
        }
        if (dispatchItemClick) {
            handled |= super.performItemClick(view, position, id);
        }
        return handled;
    }

    private final class ListAdapterWrapper implements ListAdapter {
        private final ListAdapter wrapped;

        public ListAdapterWrapper(ListAdapter wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public int getCount() {
            return wrapped.getCount();
        }

        @Override
        public Object getItem(int position) {
            return wrapped.getItem(position);
        }

        @Override
        public long getItemId(int position) {
            return wrapped.getItemId(position);
        }

        @Override
        public int getItemViewType(int position) {
            return wrapped.getItemViewType(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return onPrepareView(wrapped.getView(position, convertView, parent), position);
        }

        @Override
        public int getViewTypeCount() {
            return wrapped.getViewTypeCount();
        }

        @Override
        public boolean hasStableIds() {
            return wrapped.hasStableIds();
        }

        @Override
        public boolean isEmpty() {
            return wrapped.isEmpty();
        }

        private DataSetObserver lastDataSetObserver;

        public void notifyDataSetChanged() {
            if (lastDataSetObserver != null) {
                lastDataSetObserver.onChanged();
            }
        }

        @Override
        public void registerDataSetObserver(DataSetObserver dataSetObserver) {
            wrapped.registerDataSetObserver(lastDataSetObserver = dataSetObserver);
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver dataSetObserver) {
            lastDataSetObserver = null;
            wrapped.unregisterDataSetObserver(dataSetObserver);
        }

        @Override
        public boolean areAllItemsEnabled() {
            return wrapped.areAllItemsEnabled();
        }

        @Override
        public boolean isEnabled(int position) {
            return wrapped.isEnabled(position);
        }
    }

    public boolean performItemLongClick(final View child,
            final int longPressPosition, final long longPressId) {
        if (mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL) {
            if (mChoiceActionMode == null &&
                    (mChoiceActionMode = startActionMode(mMultiChoiceModeCallback)) != null) {
                setItemChecked(longPressPosition, true);
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            return true;
        }
        boolean handled = false;
        if (mOnItemLongClickListenerWrapper.wrapped != null) {
            handled = mOnItemLongClickListenerWrapper.wrapped.onItemLongClick(ListView.this, child,
                    longPressPosition, longPressId);
        }
        if (!handled) {
            mContextMenuInfo = createContextMenuInfo(child, longPressPosition, longPressId);
            handled = super.showContextMenuForChild(ListView.this);
        }
        if (handled) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        return handled;
    }

    protected View onPrepareView(View view, int position) {
        if (mEnableModalBackgroundWrapper && !(view instanceof ModalBackgroundWrapper)) {
            if (view.getParent() != null) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
            ModalBackgroundWrapper wrapper = new ModalBackgroundWrapper(getContext());
            wrapper.addView(view);
            view = wrapper;
        } else if (!mEnableModalBackgroundWrapper && view instanceof ModalBackgroundWrapper) {
            view = ((ModalBackgroundWrapper) view).getChildAt(0);
        }
        if (mCheckStates != null) {
            final boolean value = mCheckStates.get(position);
            setStateOnView(view, value);
        } else {
            setStateOnView(view, false);
        }
        return view;
    }

    public final void setActivity(Activity activity) {
        mActivity = activity;
        if (mActivity != null) {
            mActivity.addOnWindowFocusChangeListener(this);
        }
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        mAdapter = adapter == null ? null : new ListAdapterWrapper(adapter);
        if (mAdapter != null) {
            mAdapterHasStableIds = mAdapter.hasStableIds();
            if (mChoiceMode != CHOICE_MODE_NONE && mAdapterHasStableIds &&
                    mCheckedIdStates == null) {
                mCheckedIdStates = new LongSparseArray<Integer>();
            }
        }
        if (mCheckStates != null) {
            mCheckStates.clear();
        }
        if (mCheckedIdStates != null) {
            mCheckedIdStates.clear();
        }
        super.setAdapter(mAdapter);
    }

    @Override
    public void setChoiceMode(int choiceMode) {
        mChoiceMode = choiceMode;
        if (mChoiceActionMode != null) {
            mChoiceActionMode.finish();
            mChoiceActionMode = null;
        }
        if (mChoiceMode != CHOICE_MODE_NONE) {
            if (mCheckStates == null) {
                mCheckStates = new SparseBooleanArray();
            }
            if (mCheckedIdStates == null && mAdapter != null && mAdapter.hasStableIds()) {
                mCheckedIdStates = new LongSparseArray<Integer>();
            }
            if (mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL) {
                clearChoices();
                setLongClickable(true);
                setEnableModalBackgroundWrapper(true);
            }
        }
    }

    @Override
    public void setItemChecked(int position, boolean value) {
        if (mChoiceMode == CHOICE_MODE_NONE) {
            return;
        }
        if (value && mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL && mChoiceActionMode == null) {
            mChoiceActionMode = startActionMode(mMultiChoiceModeCallback);
        }
        if (mChoiceMode == CHOICE_MODE_MULTIPLE || mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL) {
            boolean oldValue = mCheckStates.get(position);
            mCheckStates.put(position, value);
            if (mCheckedIdStates != null && mAdapter.hasStableIds()) {
                if (value) {
                    mCheckedIdStates.put(mAdapter.getItemId(position), position);
                } else {
                    mCheckedIdStates.delete(mAdapter.getItemId(position));
                }
            }
            if (oldValue != value) {
                if (value) {
                    mCheckedItemCount++;
                } else {
                    mCheckedItemCount--;
                }
            }
            if (mChoiceActionMode != null) {
                final long id = mAdapter.getItemId(position);
                mMultiChoiceModeCallback.onItemCheckedStateChanged(mChoiceActionMode,
                        position, id, value);
            }
        } else {
            boolean updateIds = mCheckedIdStates != null && mAdapter.hasStableIds();
            if (value || isItemChecked(position)) {
                mCheckStates.clear();
                if (updateIds) {
                    mCheckedIdStates.clear();
                }
            }
            if (value) {
                mCheckStates.put(position, true);
                if (updateIds) {
                    mCheckedIdStates.put(mAdapter.getItemId(position), position);
                }
                mCheckedItemCount = 1;
            } else if (mCheckStates.size() == 0 || !mCheckStates.valueAt(0)) {
                mCheckedItemCount = 0;
            }
        }
        invalidateViews();
        updateOnScreenCheckedViews();
    }

    public void setMultiChoiceModeListener(MultiChoiceModeListener listener) {
        if (mMultiChoiceModeCallback == null) {
            mMultiChoiceModeCallback = new MultiChoiceModeWrapper();
        }
        mMultiChoiceModeCallback.setWrapped(listener);
    }

    @Override
    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        mOnItemLongClickListenerWrapper.setWrapped(listener);
    }

    @Override
    public boolean showContextMenuForChild(View originalView) {
        final int longPressPosition = getPositionForView(originalView);
        if (longPressPosition >= 0) {
            final long longPressId = mAdapter.getItemId(longPressPosition);
            boolean handled = false;
            if (mOnItemLongClickListenerWrapper.wrapped != null) {
                handled = mOnItemLongClickListenerWrapper.wrapped.onItemLongClick(ListView.this,
                        originalView, longPressPosition, longPressId);
            }
            if (!handled) {
                mContextMenuInfo = createContextMenuInfo(getChildAt(longPressPosition
                        - getFirstVisiblePosition()), longPressPosition, longPressId);
                handled = super.showContextMenuForChild(originalView);
            }
            return handled;
        }
        return false;
    }

    public ActionMode startActionMode(ActionMode.Callback callback) {
        if (mActivity != null) {
            return mActivity.startActionMode(callback);
        }
        throw new RuntimeException("HoloEverywhere.ListView (" + this
                + ") don't have reference on Activity");
    }

    private static final boolean USE_ACTIVATED = VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB;

    private void updateOnScreenCheckedViews() {
        final int firstPos = getFirstVisiblePosition();
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            final int position = firstPos + i;
            final boolean value = mCheckStates.get(position);
            setStateOnView(child, value);
        }
    }

    protected final void setStateOnView(View child, boolean value) {
        if (child instanceof Checkable) {
            ((Checkable) child).setChecked(value);
        } else if (USE_ACTIVATED) {
            child.setActivated(value);
        }
    }
}
