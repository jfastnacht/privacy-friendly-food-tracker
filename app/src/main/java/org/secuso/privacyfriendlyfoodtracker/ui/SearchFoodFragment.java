/*
This file is part of Privacy friendly food tracker.

Privacy friendly food tracker is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Privacy friendly food tracker is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Privacy friendly food tracker.  If not, see <https://www.gnu.org/licenses/>.
*/
package org.secuso.privacyfriendlyfoodtracker.ui;

import android.app.Activity;
import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.CardView;
import android.support.v7.widget.DividerItemDecoration;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.LinearLayoutManager;

import org.secuso.privacyfriendlyfoodtracker.R;
import org.secuso.privacyfriendlyfoodtracker.ui.adapter.DatabaseFacade;
import org.secuso.privacyfriendlyfoodtracker.ui.adapter.SearchResultAdapter;
import org.secuso.privacyfriendlyfoodtracker.database.Product;
import org.secuso.privacyfriendlyfoodtracker.network.ApiManager;
import org.secuso.privacyfriendlyfoodtracker.network.ProductApiService;
import org.secuso.privacyfriendlyfoodtracker.network.models.NetworkProduct;
import org.secuso.privacyfriendlyfoodtracker.network.models.ProductResponse;
import org.secuso.privacyfriendlyfoodtracker.network.models.ProductApiResponse;
import org.secuso.privacyfriendlyfoodtracker.network.utils.ProductConversionHelper;
import org.secuso.privacyfriendlyfoodtracker.ui.viewmodels.SharedStatisticViewModel;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

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
    DatabaseFacade databaseFacade;
    private RecyclerView foodList;
    private LinearLayoutManager llm;

    /**
     * Required empty public constructor
     */
    public SearchFoodFragment() {
        // Required empty public constructor
    }

    ProductResponse productResponse = new ProductResponse();
    List<Product> products;

    private static int pageNumber = 0;

    /**
     * Called when the activity is created
     * @param inflater the layout inflater
     * @param container the container
     * @param savedInstanceState the saved instance state
     * @return the outermost layout
     */
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
                            float calories = Float.parseFloat(cal);

                            TextView carbView = childView.findViewById(R.id.resultCarbs);
                            String carbsString = carbView.getText().toString();
                            carbsString = carbsString.split(" ")[0];
                            float carbs = Float.parseFloat(carbsString);

                            String sugarString = carbView.getText().toString();
                            sugarString = sugarString.split("\\(")[1].split("\\)")[0];
                            float sugar = Float.parseFloat(sugarString);

                            TextView protView = childView.findViewById(R.id.resultProtein);
                            String prot = protView.getText().toString();
                            prot = prot.split(" ")[0];
                            float protein = Float.parseFloat(prot);

                            TextView fatView = childView.findViewById(R.id.resultFat);
                            String fatS = fatView.getText().toString();
                            fatS = fatS.split(" ")[0];
                            float fat = Float.parseFloat(fatS);

                            String satFatS = fatView.getText().toString();
                            satFatS = satFatS.split("\\(")[1].split("\\)")[0];
                            float satFat = Float.parseFloat(satFatS);

                            ((BaseAddFoodActivity) referenceActivity).id = id;
                            ((BaseAddFoodActivity) referenceActivity).name = name;
                            ((BaseAddFoodActivity) referenceActivity).calories = calories;
                            ((BaseAddFoodActivity) referenceActivity).carbs = carbs;
                            ((BaseAddFoodActivity) referenceActivity).sugar = sugar;
                            ((BaseAddFoodActivity) referenceActivity).protein = protein;
                            ((BaseAddFoodActivity) referenceActivity).fat = fat;
                            ((BaseAddFoodActivity) referenceActivity).satFat = satFat;
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
                SearchResultAdapter newAdapter = new SearchResultAdapter(
                        databaseFacade.getProductByName(s.toString())
                );
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

        FloatingActionButton searchFab = (FloatingActionButton) parentHolder.findViewById(R.id.search_fab);
        searchFab.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                pageNumber = 0;
                ProductApiService mProductApiService = ApiManager.getInstance().getProductApiService();

                // If the search string was only digits, treat it as a barcode instead of a text search
                if (TextUtils.isDigitsOnly(search.getText())) {
                    Call<ProductApiResponse> call = mProductApiService.getProductFromBarcode(search.getText().toString());
                    call.enqueue(new Callback<ProductApiResponse>() {
                        @Override
                        public void onResponse(Call<ProductApiResponse> call, Response<ProductApiResponse> response) {
                            if (response.isSuccessful()) {
                                ProductApiResponse product_response = response.body();
                                if (product_response.status == 0) {
                                    String toast = "Error fetching product information: " + product_response.status_verbose;
                                    Toast.makeText(getActivity(), toast, Toast.LENGTH_SHORT).show();
                                } else {
                                    Product product = ProductConversionHelper.conversionProduct(product_response.product);
                                    ((BaseAddFoodActivity) referenceActivity).id = product.id;
                                    ((BaseAddFoodActivity) referenceActivity).name = product.name;
                                    ((BaseAddFoodActivity) referenceActivity).calories = product.energy;
                                    ((BaseAddFoodActivity) referenceActivity).productSet = true;
                                    ViewPager pager = referenceActivity.findViewById(R.id.pager_food);
                                    // 1 is the 'AddFoodFragment'
                                    pager.setCurrentItem(1);
                                }
                            } else {
                                String error = "Error fetching product information: " + Integer.toString(response.code());
                                Toast.makeText(getActivity(), error, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ProductApiResponse> call, Throwable t) {
                            t.printStackTrace();
                        }
                    });

                } else {
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

                }
                return true;
            }
        });

        return parentHolder;
    }
}
