package eu.kaguya.service.remote;

import eu.kaguya.dto.Currency;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface RemoteService {

	@GET("latest")
	public Call<Currency> latest(@Query("symbols") String symbolsString, @Query("base") String baseCurrencyString);
	
}
