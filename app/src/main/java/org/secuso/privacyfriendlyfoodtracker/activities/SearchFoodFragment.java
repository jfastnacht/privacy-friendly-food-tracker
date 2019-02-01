package org.secuso.privacyfriendlyfoodtracker.activities;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.CardView;
import android.support.v7.widget.DividerItemDecoration;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.LinearLayoutManager;
import android.widget.Toast;

import org.secuso.privacyfriendlyfoodtracker.R;
import org.secuso.privacyfriendlyfoodtracker.activities.adapter.DatabaseFacade;
import org.secuso.privacyfriendlyfoodtracker.activities.adapter.SearchResultAdapter;
import org.secuso.privacyfriendlyfoodtracker.activities.helper.DateHelper;
import org.secuso.privacyfriendlyfoodtracker.database.ApplicationDatabase;
import org.secuso.privacyfriendlyfoodtracker.database.ConsumedEntrieAndProductDao;
import org.secuso.privacyfriendlyfoodtracker.database.Product;
import org.secuso.privacyfriendlyfoodtracker.network.ApiManager;
import org.secuso.privacyfriendlyfoodtracker.network.ProductApiService;
import org.secuso.privacyfriendlyfoodtracker.network.models.NetworkProduct;
import org.secuso.privacyfriendlyfoodtracker.network.models.ProductResponse;
import org.secuso.privacyfriendlyfoodtracker.network.utils.ProductConversionHelper;
import org.secuso.privacyfriendlyfoodtracker.viewmodels.SharedStatisticViewModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


/**
 * A simple {@link Fragment} subclass. Contains a possibility to search for a product.
 *
 * @author Simon Reinkemeier
 */
public class SearchFoodFragment extends Fragment {
    SharedStatisticViewModel sharedStatisticViewModel;
    Activity referenceActivity;
    View parentHolder;
    TextView textView;
    DatabaseFacade databaseFacade;
    private RecyclerView foodList;
    private LinearLayoutManager llm;


    public SearchFoodFragment() {
        // Required empty public constructor
    }

    ProductResponse productResponse = new ProductResponse();
    List<Product> products;

    private static int pageNumber = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        referenceActivity = getActivity();
        parentHolder = inflater.inflate(R.layout.content_search, container, false);
        sharedStatisticViewModel = ViewModelProviders.of(getActivity()).get(SharedStatisticViewModel.class);
        try {
            databaseFacade = new DatabaseFacade(referenceActivity.getApplicationContext());
        } catch (Exception e) {
            Log.e("Error", e.getMessage());
        }

        foodList = (RecyclerView) parentHolder.findViewById(R.id.search_results);
        llm = new LinearLayoutManager(referenceActivity.getApplicationContext());


        foodList.setLayoutManager(llm);

        foodList.addItemDecoration(new DividerItemDecoration(referenceActivity.getApplicationContext(), LinearLayoutManager.VERTICAL));

        final SearchResultAdapter adapter = new SearchResultAdapter(databaseFacade.findMostCommonProducts());
        final EditText search = parentHolder.findViewById(R.id.search_term);

        foodList.setAdapter(adapter);
        foodList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (!recyclerView.canScrollVertically(1)) {
                    // Toast.makeText(referenceActivity, "endOfScroll", Toast.LENGTH_LONG).show();

                    ProductApiService mProductApiService = ApiManager.getInstance().getProductApiService();
                    Call<ProductResponse> call = mProductApiService.listProductsFromPage(search.getText().toString(), String.valueOf(pageNumber++));
                    call.enqueue(new Callback<ProductResponse>() {
                        @Override
                        public void onResponse(Call<ProductResponse> call, Response<ProductResponse> response) {
                            if (response.isSuccessful()) {
                                productResponse = response.body();
                                products = new LinkedList<Product>();
                                for (int i = 0; i < productResponse.getProducts().size(); i++) {
                                    NetworkProduct product = productResponse.getProducts().get(i);
                                    Product convertedProd = ProductConversionHelper.conversionProduct(product);
                                    if (convertedProd != null) {
                                        products.add(convertedProd);
                                    }
                                }
                                if (products.size() != 0) {
                                    SearchResultAdapter searchResultAdapter = (SearchResultAdapter) foodList.getAdapter();
                                    searchResultAdapter.addItems(products);
                                    pageNumber++;
                                }
                            } else {
                                //show error
                                System.out.println("Not success");
                            }
                        }
                        @Override
                        public void onFailure(Call<ProductResponse> call, Throwable t) {
                            t.printStackTrace();
                        }
                    });
                }
            }
        });

       foodList.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            private static final int MAX_CLICK_DURATION = 100;
            private long startClickTime;
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent motionEvent) {
                switch (motionEvent.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: {
                        startClickTime = Calendar.getInstance().getTimeInMillis();
                        break;
                    }
                    case MotionEvent.ACTION_UP: {
                        long clickDuration = Calendar.getInstance().getTimeInMillis() - startClickTime;
                        if(clickDuration < MAX_CLICK_DURATION) {
                            // click event has occurred
                            // Toast.makeText(referenceActivity, "shortClick", Toast.LENGTH_LONG).show();
                            CardView childView = (CardView) recyclerView.findChildViewUnder(motionEvent.getX(), motionEvent.getY());
                            if(null == childView){
                                return false;
                            }
                            View x = recyclerView.findChildViewUnder(motionEvent.getX(), motionEvent.getY());
                            int id = 0;
                            try {
                                TextView idView = childView.findViewById(R.id.resultId);
                                id = Integer.parseInt(idView.getText().toString());
                            } catch (NullPointerException exc){
                                exc.printStackTrace();
                            }
                            TextView nameView = childView.findViewById(R.id.resultName);
                            String name = nameView.getText().toString();

                            TextView calView = childView.findViewById(R.id.resultCalories);
                            String cal = calView.getText().toString();
                            cal = cal.split(" ")[0];
                            int calories = Integer.parseInt(cal);

                            ((BaseAddFoodActivity) referenceActivity).id = id;
                            ((BaseAddFoodActivity) referenceActivity).name = name;
                            ((BaseAddFoodActivity) referenceActivity).calories = calories;
                            ((BaseAddFoodActivity) referenceActivity).productSet = true;
                            ViewPager pager = referenceActivity.findViewById(R.id.pager_food);
                            System.out.println("Setting page");
                            // 1 is the 'AddFoodFragment'
                            pager.setCurrentItem(1);
                        }
                    }
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent e) {


            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean b) {

            }
        });


        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // search in the local db first
                System.out.println(s.toString());
                SearchResultAdapter newAdapter = new SearchResultAdapter(databaseFacade.getProductByName(s.toString()));
                foodList.setAdapter(newAdapter);
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                System.out.println("Action");
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    pageNumber = 0;
                    ProductApiService mProductApiService = ApiManager.getInstance().getProductApiService();
                    Call<ProductResponse> call = mProductApiService.listProducts(search.getText().toString());
                    call.enqueue(new Callback<ProductResponse>() {
                        @Override
                        public void onResponse(Call<ProductResponse> call, Response<ProductResponse> response) {
                            if (response.isSuccessful()) {
                                productResponse = response.body();
                                products = new LinkedList<Product>();
                                for (int i = 0; i < productResponse.getProducts().size(); i++) {
                                    NetworkProduct product = productResponse.getProducts().get(i);
                                    Product convertedProd = ProductConversionHelper.conversionProduct(product);
                                    if (convertedProd != null) {
                                        products.add(convertedProd);
                                    }
                                }
                                SearchResultAdapter newAdapter = new SearchResultAdapter(products);
                                foodList.setAdapter(newAdapter);
                                foodList.addOnScrollListener(new RecyclerView.OnScrollListener() {


                                    @Override
                                    public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                                        super.onScrollStateChanged(recyclerView, newState);
                                    }
                                });
                            } else {
                                //show error
                                System.out.println("Not success");
                            }
                        }

                        @Override
                        public void onFailure(Call<ProductResponse> call, Throwable t) {
                            t.printStackTrace();
                        }
                    });

                    return true;
                }
                return false;
            }
        });
        return parentHolder;
    }
}