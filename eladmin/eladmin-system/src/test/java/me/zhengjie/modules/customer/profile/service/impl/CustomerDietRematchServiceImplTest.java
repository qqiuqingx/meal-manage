package me.zhengjie.modules.customer.profile.service.impl;

import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.service.CustomerDietDictionaryService;
import me.zhengjie.modules.customer.profile.service.CustomerDietMatchService;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.mockito.Mockito.*;

class CustomerDietRematchServiceImplTest {
    @Test
    void advancesCursorAfterFailureAndReusesOneSnapshotPerRound() {
        CustomerProfileMapper profiles = mock(CustomerProfileMapper.class);
        CustomerDietDictionaryService dictionary = mock(CustomerDietDictionaryService.class);
        CustomerDietMatchService matcher = spy(new CustomerDietMatchService());
        CustomerDietRestrictionWriter writer = mock(CustomerDietRestrictionWriter.class);
        when(dictionary.listActiveOptions()).thenReturn(Collections.emptyList());
        when(profiles.findDietRematchIds(0, 100)).thenReturn(Arrays.asList(10L, 11L));
        when(profiles.findDietRematchIds(11, 100)).thenReturn(Collections.singletonList(20L));
        when(profiles.findDietRematchIds(20, 100)).thenReturn(Collections.emptyList());
        when(writer.rematch(eq(10L), any())).thenThrow(new IllegalStateException("customer failure"));
        when(writer.rematch(eq(11L), any())).thenReturn(true);

        new CustomerDietRematchServiceImpl(profiles, dictionary, matcher, writer).rematchAll();

        verify(writer).rematch(eq(20L), any());
        verify(dictionary, times(1)).listActiveOptions();
        verify(matcher, times(1)).snapshot(any());
        org.mockito.ArgumentCaptor<CustomerDietMatchService.DictionarySnapshot> snapshots =
                org.mockito.ArgumentCaptor.forClass(CustomerDietMatchService.DictionarySnapshot.class);
        verify(writer, times(3)).rematch(anyLong(), snapshots.capture());
        org.junit.jupiter.api.Assertions.assertSame(snapshots.getAllValues().get(0), snapshots.getAllValues().get(2));
    }
}
